/**  
 * @Title: WordCount.java
 * @Package com.spark
 * @author houzwhouzw
 * @createdate 2017年8月3日
 * @Description: TODO
 * @function list:
 * @--------------------edit history-----------------
 * @editdate 2017年8月3日
 * @editauthor houzw
 * @Description TODO
 */
package com.spark.test;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.Function2;
import org.apache.spark.storage.StorageLevel;

/**
 * ClassName: WordCount 
 * @author houzw
 * @date 2017年8月3日
 * @Description: TODO
 */
public class WordCount {
	// 完成对所有行的长度求和
    public static void main(String[] args) {

        SparkConf conf = new SparkConf().setMaster("local").setAppName("qqq");
        conf.set("spark.testing.memory", "2147480000"); // 因为jvm无法获得足够的资源
        JavaSparkContext sc = new JavaSparkContext(conf);
        System.out.println(sc);


        //通过hdfs上的文件定义一个RDD 这个数据暂时还没有加载到内存，也没有在上面执行动作,lines仅仅指向这个文件
        JavaRDD<String> lines = sc.textFile("hdfs://localhost:9000/user/input/input.txt");
        //定义lineLengths作为Map转换的结果 由于惰性，不会立即计算lineLengths
        JavaRDD<Integer> lineLengths = lines.map(new GetLength());


        //运行reduce  这是一个动作action  这时候，spark才将计算拆分成不同的task，
                //并运行在独立的机器上，每台机器运行他自己的map部分和本地的reducation，并返回结果集给去驱动程序
        int totalLength = lineLengths.reduce(new Sum());

        System.out.println("总长度"+totalLength);
        // 为了以后复用 持久化到内存...
        lineLengths.persist(StorageLevel.MEMORY_ONLY());

    }
    //定义map函数
    //第一个参数为传入的内容，第二个参数为函数操作完后返回的结果类型
    static class GetLength implements Function<String, Integer> {
        /**
		 * @Fields serialVersionUID : TODO
		 */
		private static final long serialVersionUID = 1L;

		public Integer call(String s) {
            return s.length();
        }
    }
    //定义reduce函数 
    //第一个参数为内容，第三个参数为函数操作完后返回的结果类型
    static class Sum implements Function2<Integer, Integer, Integer> {
        /**
		 * @Fields serialVersionUID : TODO
		 */
		private static final long serialVersionUID = 1L;

		public Integer call(Integer a, Integer b) {
            return a + b;
        }
    }
}
