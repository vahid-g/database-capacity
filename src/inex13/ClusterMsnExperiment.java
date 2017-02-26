package inex13;

import inex09.ClusterDirectoryInfo;
import inex09.InexQueryServices;
import inex09.MsnQuery;
import inex09.MsnQueryResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.FileUtils;

public class ClusterMsnExperiment {

	static final Logger LOGGER = Logger.getLogger(ClusterMsnExperiment.class
			.getName());

	public static void main(String[] args) {
		File indexBaseDir = new File(ClusterDirectoryInfo.LOCAL_INDEX_BASE13);
		if (!indexBaseDir.exists())
			indexBaseDir.mkdirs();
		File resultDir = new File(ClusterDirectoryInfo.RESULT_DIR);
		if (!resultDir.exists())
			resultDir.mkdirs();

		// float gamma = Float.parseFloat(args[0]);
		// gridSearchExperiment(gamma);

		int expNo = Integer.parseInt(args[0]);
		int totalExpNo = Integer.parseInt(args[1]);
		long start_t = System.currentTimeMillis();
		expText(expNo, totalExpNo);
		long end_t = System.currentTimeMillis();
		LOGGER.log(Level.INFO, "Time spent for experiment " + expNo + " is "
				+ (end_t - start_t) / 60000 + " minutes");
	}

	static void gridSearchExperiment(float gamma) {
		List<PathCountTitle> pathCountList = loadFilePathCountTitle(ClusterDirectoryInfo.PATH_COUNT_FILE13);
		// TODO sort?
		LOGGER.log(Level.INFO,
				"Number of loaded path_counts: " + pathCountList.size());
		String indexName = ClusterDirectoryInfo.LOCAL_INDEX_BASE13
				+ "inex13_grid_" + (gamma * 10);
		LOGGER.log(Level.INFO, "Building index..");
		InexIndexer.buildIndex(pathCountList, indexName, gamma);
		LOGGER.log(Level.INFO, "Loading and running queries..");
		List<MsnQuery> queries = InexQueryServices.loadMsnQueries(
				ClusterDirectoryInfo.MSN_QUERY_QID_S,
				ClusterDirectoryInfo.MSN_QID_QREL);
		LOGGER.log(Level.INFO, "Number of loaded queries: " + queries.size());
		List<MsnQueryResult> results = InexQueryServices.runMsnQueries(queries,
				indexName);
		LOGGER.log(Level.INFO, "Writing results to file..");
		try (FileWriter fw = new FileWriter(ClusterDirectoryInfo.RESULT_DIR
				+ "inex13_grid_" + (gamma * 10) + ".csv")) {
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

	public static void expText(int expNo, int totalExp) {
		List<PathCountTitle> pathCountList = loadFilePathCountTitle(ClusterDirectoryInfo.PATH_COUNT_FILE13);
		double total = (double) totalExp;
		pathCountList = pathCountList.subList(0,
				(int) (((double) expNo / total) * pathCountList.size()));
		LOGGER.log(Level.INFO,
				"Number of loaded path_counts: " + pathCountList.size());
		LOGGER.log(Level.INFO, "Best score: " + pathCountList.get(0).visitCount);
		LOGGER.log(
				Level.INFO,
				"Smallest score: "
						+ pathCountList.get(pathCountList.size() - 1).visitCount);
		LOGGER.log(Level.INFO, "Building index..");
		String indexName = ClusterDirectoryInfo.LOCAL_INDEX_BASE13 + "index13_"
				+ expNo;
		InexIndexer.buildIndexOnTextWless(pathCountList, indexName, 0.9f);

		LOGGER.log(Level.INFO, "Loading and running queries..");
		List<MsnQuery> queries = InexQueryServices.loadMsnQueries(
				ClusterDirectoryInfo.MSN_QUERY_QID_B,
				ClusterDirectoryInfo.MSN_QID_QREL);
		LOGGER.log(Level.INFO, "Number of loaded queries: " + queries.size());
		List<MsnQueryResult> results = InexQueryServices.runMsnQueries(queries,
				indexName);
		LOGGER.log(Level.INFO, "Writing results..");
		try (FileWriter fw = new FileWriter(ClusterDirectoryInfo.RESULT_DIR
				+ "msn13_" + totalExp + "_" + expNo + ".csv")) {
			for (MsnQueryResult mqr : results) {
				fw.write(mqr.fullResult() + "\n");
			}
			LOGGER.log(Level.INFO, "cleanup..");
			FileUtils.deleteDirectory(new File(indexName));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static List<PathCountTitle> loadFilePathCountTitle(
			String pathCountTitleFile) {
		LOGGER.log(Level.INFO, "Loading path-count-titles..");
		List<PathCountTitle> pathCountList = new ArrayList<PathCountTitle>();
		try (BufferedReader br = new BufferedReader(new FileReader(
				pathCountTitleFile))) {
			String line;
			while ((line = br.readLine()) != null) {
				try {
					if (!line.contains(","))
						continue;
					String[] fields = line.split(",");
					String path = ClusterDirectoryInfo.CLUSTER_BASE + fields[0];
					Integer count = Integer.parseInt(fields[1].trim());
					String title = fields[2].trim();
					pathCountList.add(new PathCountTitle(path, count, title));
				} catch (Exception e) {
					LOGGER.log(Level.WARNING, "Couldn't read PathCountTitle: "
							+ line + " cause: " + e.toString());
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return pathCountList;
	}

	// how to load inex13 queries:
	//
	// String QUERY_FILE = ClusterDirectoryInfo.CLUSTER_BASE +
	// "data/queries/inex_ld/2013-ld-adhoc-topics.xml"; String QREL_FILE =
	// ClusterDirectoryInfo.CLUSTER_BASE +
	// "data/queries/inex_ld/2013-ld-adhoc-qrels/2013LDT-adhoc.qrels";
	// HashMap<Integer, InexQueryDAO> queriesMap =
	// QueryParser.buildQueries(QUERY_FILE, QREL_FILE); List<InexQueryDAO>
	// queries = new ArrayList<InexQueryDAO>();
	// queries.addAll(queriesMap.values()); List<InexQueryResult> resultList =
	// QueryServices.runQueries(queries, indexPath);

}