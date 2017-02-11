package inex13;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

import freebase.FreebaseDatabaseSizeExperiment;
import inex09.ClusterDirectoryInfo;
import inex09.InexQueryServices;
import inex09.MsnQuery;
import inex09.MsnQueryResult;

public class ClusterMsnExperiment {

	final static String FILE_LIST = ClusterDirectoryInfo.CLUSTER_BASE + "data/filelist.txt";
	final static String QUERY_FILE = ClusterDirectoryInfo.CLUSTER_BASE
			+ "data/queries/inex_ld/2013-ld-adhoc-topics.xml";
	final static String QREL_FILE = ClusterDirectoryInfo.CLUSTER_BASE
			+ "data/queries/inex_ld/2013-ld-adhoc-qrels/2013LDT-adhoc.qrels";
	final static String RESULT_DIR = ClusterDirectoryInfo.CLUSTER_BASE + "data/result/inex13_dbsize/";

	static final Logger LOGGER = Logger.getLogger(FreebaseDatabaseSizeExperiment.class.getName());

	static class PathVisitCountTuple {
		String path;
		Integer visitCount;

		public PathVisitCountTuple(String path, Integer visitCount) {
			this.path = path;
			this.visitCount = visitCount;
		}
	}

	public static void main(String[] args) {
		// initializations
		LOGGER.setUseParentHandlers(false);
		Handler handler = new ConsoleHandler();
		handler.setLevel(Level.ALL);
		LOGGER.addHandler(handler);
		LOGGER.setLevel(Level.ALL);
		File indexBaseDir = new File(ClusterDirectoryInfo.LOCAL_INDEX_BASE13);
		if (!indexBaseDir.exists())
			indexBaseDir.mkdirs();
		File resultDir = new File(ClusterDirectoryInfo.RESULT_DIR);
		if (!resultDir.exists())
			resultDir.mkdirs();

		// float gamma = Float.parseFloat(args[0]);
		// gridSearchExperiment(gamma);

		int expNo = Integer.parseInt(args[0]);
		long start_t = System.currentTimeMillis();
		exp(expNo);
		long end_t = System.currentTimeMillis();
		LOGGER.log(Level.INFO, "Time spent for experiment " + expNo + " is " + (end_t - start_t) / 60000 + " minutes");
	}

	static void gridSearchExperiment(float gamma) {
		List<PathVisitCountTuple> pathCountList = loadFilePathPageVisit();
		// TODO sort?
		LOGGER.log(Level.INFO, "Number of loaded path_counts: " + pathCountList.size());
		String indexName = ClusterDirectoryInfo.LOCAL_INDEX_BASE13 + "inex13_grid_" + (gamma * 10);
		LOGGER.log(Level.INFO, "Building index..");
		InexIndexer.buildIndex(pathCountList, indexName, gamma);
		LOGGER.log(Level.INFO, "Loading and running queries..");
		List<MsnQuery> queries = InexQueryServices.loadMsnQueries(ClusterDirectoryInfo.MSN_QUERY_QID_S,
				ClusterDirectoryInfo.MSN_QID_QREL);
		LOGGER.log(Level.INFO, "Number of loaded queries: " + queries.size());
		List<MsnQueryResult> results = InexQueryServices.runMsnQueries(queries, indexName);
		LOGGER.log(Level.INFO, "Writing results to file..");
		try (FileWriter fw = new FileWriter(ClusterDirectoryInfo.RESULT_DIR + "inex13_grid_" + (gamma * 10) + ".csv")) {
			for (MsnQueryResult mqr : results) {
				fw.write(mqr.toString());
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		try {
			LOGGER.log(Level.INFO, "cleanup..");
			FileUtils.deleteDirectory(new File(indexName));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void exp(int expNo) {
		LOGGER.log(Level.INFO, "Loading files list and counts");
		List<PathVisitCountTuple> pathCountList = loadFilePathPageVisit();
		pathCountList = pathCountList.subList(0, (int)((expNo / 10.0) * pathCountList.size()));
		LOGGER.log(Level.INFO, "Number of loaded path_counts: " + pathCountList.size());
		LOGGER.log(Level.INFO, "Building index..");
		String indexName = ClusterDirectoryInfo.LOCAL_INDEX_BASE13 + "inex13_" + expNo;
		InexIndexer.buildIndex(pathCountList, indexName, 0); // TODO set gamma

		LOGGER.log(Level.INFO, "Loading and running queries..");
		List<MsnQuery> queries = InexQueryServices.loadMsnQueries(ClusterDirectoryInfo.MSN_QUERY_QID_B,
				ClusterDirectoryInfo.MSN_QID_QREL);
		LOGGER.log(Level.INFO, "Number of loaded queries: " + queries.size());
		List<MsnQueryResult> results = InexQueryServices.runMsnQueries(queries, indexName);
		LOGGER.log(Level.INFO, "Writing results..");
		try (FileWriter fw = new FileWriter(ClusterDirectoryInfo.RESULT_DIR + "inex_" + expNo + ".csv")) {
			for (MsnQueryResult mqr : results) {
				fw.write(mqr.toString());
			}
			LOGGER.log(Level.INFO, "cleanup..");
			FileUtils.deleteDirectory(new File(indexName));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static List<PathVisitCountTuple> loadFilePathPageVisit() {
		LOGGER.log(Level.INFO, "Loading files list and sorted counts..");
		List<PathVisitCountTuple> pathCountList = new ArrayList<PathVisitCountTuple>();
		try (BufferedReader br = new BufferedReader(new FileReader(ClusterDirectoryInfo.PATH_COUNT_FILE13))) {
			String line;
			while ((line = br.readLine()) != null) {
				if (!line.contains(","))
					continue;
				String path = ClusterDirectoryInfo.CLUSTER_BASE + line.split(",")[0];
				Integer count = Integer.parseInt(line.split(",")[1].trim());
				pathCountList.add(new PathVisitCountTuple(path, count));
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return pathCountList;
	}

	static void oldExperiment() {
		int expNo = Integer.parseInt("???");
		String indexPath = ClusterDirectoryInfo.LOCAL_INDEX_BASE13 + "exp_" + expNo;
		randomizedDbSizeSinglePartition(expNo, indexPath);
		File indexFile = new File(indexPath);
		// cleanup
		try {
			FileUtils.deleteDirectory(indexFile);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * builds index for a single partition and runs the queries on it. To be
	 * able to run this method you need to have a list of all dataset files.
	 * Then based on the expNo parameter the method will pick a subset of the
	 * files and run the code.
	 * 
	 * @param expNo
	 *            number of experiment that will also impact the partition size
	 */
	static void randomizedDbSizeSinglePartition(int expNo, String indexPath) {
		DateFormat df = new SimpleDateFormat("dd/MM/yy HH:mm:ss");
		System.out.println(df.format(new Date()) + " running experiment " + expNo);
		List<String> fileList = null;
		try {
			fileList = Files.readAllLines(Paths.get(FILE_LIST), StandardCharsets.UTF_8);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		List<String> expFileList = fileList.subList(0, (int) ((expNo / 10.0) * fileList.size()));
		System.out.println(df.format(new Date()) + " building index..");
		inex09.InexIndexer.buildIndex(expFileList.toArray(new String[0]), indexPath, false);

		// running queries
		System.out.println(df.format(new Date()) + " running queries..");
		HashMap<Integer, InexQueryDAO> queriesMap = QueryParser.buildQueries(QUERY_FILE, QREL_FILE);
		List<InexQueryDAO> queries = new ArrayList<InexQueryDAO>();
		queries.addAll(queriesMap.values());
		double p20Map[] = new double[queries.size()];
		double p3Map[] = new double[queries.size()];
		List<InexQueryResult> resultList = QueryServices.runQueries(queries, indexPath);
		for (int j = 0; j < resultList.size(); j++) {
			p20Map[j] = resultList.get(j).precisionAtK(20);
			p3Map[j] = resultList.get(j).precisionAtK(3);
		}
		// writing results to file
		System.out.println(df.format(new Date()) + " writing output to file..");
		FileWriter fw = null;
		try {
			fw = new FileWriter(RESULT_DIR + "inex_" + expNo + ".csv");
			for (int i = 0; i < p20Map.length; i++) {
				fw.write("\"" + queries.get(i).text + "\"" + ", ");
				fw.write(p20Map[i] + ", " + p3Map[i] + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (fw != null) {
				try {
					fw.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

}
