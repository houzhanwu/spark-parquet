/**  
 * @Title: HDFSUtil.java
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
package com.spark.util;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.log4j.Logger;

/**
 * ClassName: HDFSUtil
 * 
 * @author houzw
 * @date 2017年8月3日
 * @Description: TODO
 */
public class HDFSUtil {
	static Logger log = Logger.getLogger(HDFSUtil.class);

	/**
	 * 
	 * @param hdfs
	 *            FileSystem 对象
	 * @param path
	 *            文件路径
	 */
	public static List<String> iteratorShowFiles(FileSystem hdfs, Path path) {
		List<String> list = new ArrayList<String>();
		try {
			if (hdfs == null || path == null) {
				System.out.println("=====HDFS目录获取失败！");
				return list;
			}
			// 获取文件列表
			FileStatus[] files = hdfs.listStatus(path);
			if (files.length > 0) {
				// 展示文件信息
				for (int i = 0; i < files.length; i++) {
					if (files[i].isDirectory()) {
						// System.out.println(">>>" + files[i].getPath() + ",
						// dir owner:" + files[i].getOwner());
						// 递归调用
						iteratorShowFiles(hdfs, files[i].getPath());
					} else if (files[i].isFile()) {

						if (files[i].getPath().getName().endsWith(".parquet")) {
							System.out.println("=====" + files[i].getPath() + ", length:" + files[i].getLen()
									+ ", owner:" + files[i].getOwner());
							list.add(files[i].getPath().toString());
						}

					}
				}
			} else {
				System.out.println("=====HDFS目录获取失败,文件不存在！");
			}
		} catch (Exception e) {
			log.info(e.getMessage(), e);
		}
		return list;
	}

	@SuppressWarnings("static-access")
	public static void main(String[] args) {
		FileSystem hdfs = null;
		try {
			Configuration config = new Configuration();
			// 程序配置
			config.set("fs.default.name", "hdfs://cttic-test");
			// config.set("hadoop.job.ugi", "feng,111111");
			// config.set("hadoop.tmp.dir", "/tmp/hadoop-fengClient");
			// config.set("dfs.replication", "1");
			// config.set("mapred.job.tracker", "master:9001");

			hdfs = FileSystem.get(URI.create("hdfs://cttic-test"), config);
			Path path = new Path("/");
			iteratorShowFiles(hdfs, path);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (hdfs != null) {
				try {
					hdfs.closeAll();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}
}
