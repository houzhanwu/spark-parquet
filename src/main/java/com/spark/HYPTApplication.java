package com.spark;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.PairFunction;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SQLContext;
import com.analysis.AllopatryAnalysis;
import com.analysis.ErrorAnalysis;
import com.analysis.MileageAnalysis;
import com.analysis.OverSpeedAnalysis;
import com.analysis.PreProcessing;
import com.analysis.RadiusAnalysis;
import com.analysis.RuntimeAnalysis;
import com.analysis.TiredAnalysis;
import com.analysis.TrackRateAnalysis;
import com.model.Position;
import com.spark.util.HDFSUtil;
import com.util.ComparatorPositionTime;
import com.util.DateUtil;
import com.util.RedisUtil;

import redis.clients.jedis.Jedis;
import scala.Tuple2;

public class HYPTApplication {
	static Logger log = Logger.getLogger(HYPTApplication.class);
	private static ComparatorPositionTime comparator = new ComparatorPositionTime();
	private static Jedis jedis = RedisUtil.getJedis();
	private static SQLContext sqlContext = null;
	public static long total = 0;
	public static String dateStr = "";

	public static final String inPath = "/data/hbase/fp/";
	public static final String defaultFsName = "hdfs://cttic-test";// hdfs://localhost:9000
	public static final String outPath = defaultFsName + "user/spark/databackup/";

	public static void getDataFromMutilParquetFile(String[] paths,final String dateStr) throws Exception, Exception {
		DataFrame usersDF = sqlContext.read().parquet(paths);
		JavaRDD<Row> bigDataRDD = usersDF.javaRDD();
		JavaRDD<Position> result = bigDataRDD.map(new Function<Row, Position>() {
			private static final long serialVersionUID = 1L;

			@Override
			public Position call(Row row) throws Exception {
				Position position = new Position();
				position.setPositiontime((long) row.getAs("positiontime"));
				position.setAltitude((int) row.getAs("altitude"));
				position.setRoadcode((int) row.getAs("roadcode"));
				position.setAccesscode((int) row.getAs("accesscode"));
				position.setCity((int) row.getAs("city"));
				position.setPlatecolor((int) row.getAs("platecolor"));
				position.setVec3((int) row.getAs("vec3"));
				position.setVec2((int) row.getAs("vec2"));
				position.setLon((int) row.getAs("lon"));
				position.setVec1((int) row.getAs("vec1"));
				position.setEncrypt((int) row.getAs("encrypt"));
				position.setAlarm((long) row.getAs("alarm"));
				position.setCuraccesscode((int) row.getAs("curaccesscode"));
				position.setState((long) row.getAs("state"));
				position.setUpdatetime((long) row.getAs("updatetime"));
				position.setLat((int) row.getAs("lat"));
				position.setVehicleno((String) row.getAs("vehicleno"));
				position.setReserved((String) row.getAs("reserved"));
				position.setErrorcode((String) row.getAs("errorcode"));
				position.setTrans((int) row.getAs("trans"));
				position.setDirection((int) row.getAs("direction"));
				return position;
			}

		});

		JavaPairRDD<String, Position> pairRDD = result.mapToPair(new PairFunction<Position, String, Position>() {

			private static final long serialVersionUID = 1L;

			public Tuple2<String, Position> call(Position t) throws Exception {
				return new Tuple2<String, Position>(t.getVehicleno() + "_" + t.getPlatecolor(), t);
			}
		});

		JavaPairRDD<String, Iterable<Position>> veh = pairRDD.groupByKey();//

		JavaRDD<Tuple2<String, String>> ts = veh
				.map(new Function<Tuple2<String, Iterable<Position>>, Tuple2<String, String>>() {

					private static final long serialVersionUID = 1L;

					@Override
					public Tuple2<String, String> call(Tuple2<String, Iterable<Position>> v1) throws Exception {
						int pointlength = 0;
						StringBuffer sbu = new StringBuffer();
						List<Position> list = new ArrayList<Position>();
						List<Position> listerr = new ArrayList<Position>();
						Iterator<Position> iterList = v1._2.iterator();
						while (iterList.hasNext()) {
							Position position = (Position) iterList.next();
							if ("0".equals(position.getErrorcode())) {
								list.add(position);
							} else {
								listerr.add(position);
							}
						}
						Collections.sort(list, comparator);
						pointlength = list.size();
						list = PreProcessing.calculation(list);
						sbu.append(pointlength).append("_");
						if (listerr.size() > 0) {
							sbu.append(ErrorAnalysis.queryErr(listerr)).append("_");
						}
						sbu.append(RuntimeAnalysis.runtimeAnalysisAndSave(list)).append("_");
						sbu.append(pointlength - list.size()).append("_");
						sbu.append(MileageAnalysis.queryMileage(list)).append("_");
						sbu.append(RadiusAnalysis.queryRadius(list)).append("_");
						sbu.append(OverSpeedAnalysis.queryContent(list)).append("_");
						sbu.append(TiredAnalysis.queryContent(list)).append("_");
						sbu.append(TrackRateAnalysis.calculation(list)).append("_");
						sbu.append(AllopatryAnalysis.queryAllopatry(list));

						return new Tuple2<String, String>(v1._1, sbu.toString());
					}
				});
		total = ts.count();
		ts.foreach(new VoidFunction<Tuple2<String, String>>() {
			private static final long serialVersionUID = 1L;

			@Override
			public void call(Tuple2<String, String> t) throws Exception {
				jedis.hset(t._1, dateStr, t._2);
			}
		});
	}

	public static void main(String[] args) throws Exception {
		long start = System.currentTimeMillis();
		try {
			if (args.length == 0) {
				dateStr = DateUtil.getDay();
			} else {
				dateStr = args[0];
			}
			// dateStr = "20170906";
			SparkConf conf = new SparkConf().setAppName("Spark");
			JavaSparkContext sc = new JavaSparkContext(conf);
			sqlContext = new SQLContext(sc);
			FileSystem hdfs = null;
			Configuration config = new Configuration();
			config.set("fs.default.name", defaultFsName);
			config.setBoolean("fs.hdfs.impl.disable.cache", true);
			hdfs = FileSystem.get(URI.create(defaultFsName), config);
			Path path = null;
			for (int m = 0; m < 24; m++) {
				if (m < 10) {
					path = new Path(inPath + dateStr + "_txt/data_" + "0" + m);
				} else {
					path = new Path(inPath + dateStr + "_txt/data_" + m);
				}
				List<String> list = HDFSUtil.iteratorShowFiles(hdfs, path);
				if (list.size()>0) {
					log.info("=======================执行目录：" + inPath + dateStr + ",文件数：" + list.size());
					String strings[] = new String[list.size()];
					for (int i = 0, j = list.size(); i < j; i++) {
						strings[i] = list.get(i);
					}
					getDataFromMutilParquetFile(strings,dateStr);
				}else{
					log.info("=======================执行目录：" + inPath + dateStr + ",文件不存在！");
				}
			}
			sc.close();
		} catch (Exception e) {
			log.error(e.getMessage(), e);
		}
		long end = System.currentTimeMillis();
		log.info("=======================任务执行完毕,共" + total + "辆车,用时：" + DateUtil.formatTime(end - start));
	}
}
