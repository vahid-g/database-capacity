package imdb;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import indexing.InexFile;
import query.ExperimentQuery;
import query.QueryResult;
import query.QueryServices;

public class ImdbExperiment {

	static final Logger LOGGER = Logger.getLogger(ImdbExperiment.class.getName());

	public static void main(String[] args) {

		long start_t = System.currentTimeMillis();

		// buildJmdbSortedPathRating("/scratch/data-sets/imdb/imdb-inex/movies");
		// float gamma1 = Float.parseFloat(args[0]);
		// float gamma2 = Float.parseFloat(args[1]);
		// gridSearchExperiment(gamma1, gamma2);
		// int expNo = Integer.parseInt(args[0]);
		// int totalCount = Integer.parseInt(args[1]);
		// float[] gammas = {0.25f, 0.25f, 0.25f, 0.25f};
		// expInex(expNo, totalCount, gammas);
		// buildGlobalIndex(expNo, totalCount);
		localGridSearchExperiment();
		// expGlobal(expNo, totalCount);
		System.out.println((System.currentTimeMillis() - start_t) / 1000);
	}

	static List<InexFile> buildSortedPathRating(String datasetPath) {
		List<InexFile> pathCount = new ArrayList<InexFile>();
		Collection<File> filePaths = FileUtils.listFiles(new File(datasetPath), null, true);
		for (File file : filePaths) {
			String filepath = file.getAbsolutePath();
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			try {
				DocumentBuilder db = dbf.newDocumentBuilder();
				org.w3c.dom.Document doc = db.parse(new File(filepath));
				NodeList nodeList = doc.getElementsByTagName("rating");
				if (nodeList.getLength() > 1) {
					LOGGER.log(Level.SEVERE, filepath + " has more than one rating entries!");
				} else if (nodeList.getLength() < 1) {
					pathCount.add(new InexFile(filepath, 0.0));
				} else {
					Node node = nodeList.item(0).getFirstChild();
					if (node.getNodeValue() != null) {
						String rating = node.getNodeValue().split(" ")[0];
						pathCount.add(new InexFile(filepath, Double.parseDouble(rating)));
					} else {
						pathCount.add(new InexFile(filepath, 0.0));
					}
				}
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		Collections.sort(pathCount, new InexFile.ReverseWeightComparator());
		try (FileWriter fw = new FileWriter(ImdbClusterDirectoryInfo.FILE_LIST)) {
			for (InexFile dfm : pathCount) {
				fw.write(dfm.path + "," + dfm.weight + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (pathCount);
	}

	static List<InexFile> buildJmdbSortedPathRating(String datasetPath) {
		LOGGER.log(Level.INFO, "loading title ~> ratingss");
		Map<String, Integer> titleRating = new HashMap<String, Integer>();
		try (BufferedReader br = new BufferedReader(new FileReader("data/movietitle_rating.csv"))) {
			String line = br.readLine();
			while (line != null) {
				String fields[] = line.split(";");
				if (fields.length == 3) {
					String title = fields[0];
					Integer numberOfVotes = Integer.parseInt(fields[1]);
					titleRating.put(title, numberOfVotes);
					if (title.contains("(VG)")) {
						title = title.replaceAll("(VG)", "");
						titleRating.put(title, numberOfVotes);
					} else if (title.contains("(V)")) {
						title = title.replaceAll("(V)", "");
						titleRating.put(title, numberOfVotes);
					} else if (title.contains("(TV)")) {
						title = title.replaceAll("(TV)", "");
						titleRating.put(title, numberOfVotes);
					}
				}
				line = br.readLine();
			}
		} catch (FileNotFoundException e) {
			LOGGER.log(Level.SEVERE, e.getStackTrace().toString());
			return null;
		} catch (IOException e1) {
			LOGGER.log(Level.SEVERE, e1.getStackTrace().toString());
		}

		LOGGER.log(Level.INFO, "loading path ~> rating");
		List<InexFile> pathCount = new ArrayList<InexFile>();
		Collection<File> filePaths = FileUtils.listFiles(new File(datasetPath), null, true);
		for (File file : filePaths) {
			String filepath = file.getAbsolutePath();
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			try {
				DocumentBuilder db = dbf.newDocumentBuilder();
				org.w3c.dom.Document doc = db.parse(new File(filepath));
				NodeList nodeList = doc.getElementsByTagName("title");
				if (nodeList.getLength() > 1) {
					LOGGER.log(Level.SEVERE, filepath + " has more than one title!");
				} else if (nodeList.getLength() < 1) {
					LOGGER.log(Level.SEVERE, filepath + " has no title!");
				} else {
					Node node = nodeList.item(0).getFirstChild();
					if (node.getNodeValue() != null) {
						String title = node.getNodeValue().trim();
						// find rating using title
						if (titleRating.containsKey(title)) {
							pathCount.add(new InexFile(filepath, titleRating.get(title)));
						} else {
							// LOGGER.log(Level.INFO,
							// "couldn't find ratings for title: " + title);
							pathCount.add(new InexFile(filepath, 0));
						}
					} else {
						LOGGER.log(Level.SEVERE, filepath + " has no title!");
					}
				}
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
			} catch (SAXException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		Collections.sort(pathCount, new InexFile.ReverseWeightComparator());
		try (FileWriter fw = new FileWriter("data/imdb_path_ratings.csv")) {
			for (InexFile dfm : pathCount) {
				fw.write(dfm.path + "," + dfm.weight + "\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return (pathCount);
	}

	// Does the grid search to find best params. This code uses query time
	// boosting (vs. index time boosting)
	// to find the optimal param set.
	public static void localGridSearchExperiment() {
		// Note that the path count should be sorted!
		List<InexFile> fileList = InexFile.loadInexFileList("/scratch/data-sets/imdb/mfullpath_votes.csv");
		LOGGER.log(Level.INFO, "Number of loaded path_counts: " + fileList.size());
		String indexName = "data/index/grid_imdb_bm";
		LOGGER.log(Level.INFO, "Building index..");
		// float[] fieldBoost = {1f, 1f, 1f, 1f, 1f};
		// new ImdbIndexer().buildIndex(fileList, indexName, new
		// BM25Similarity(), fieldBoost);
		LOGGER.log(Level.INFO, "Loading and running queries..");
		List<ExperimentQuery> queries = QueryServices.loadInexQueries("data/queries/imdb/all-topics.xml",
				"data/queries/imdb/all.qrels", "title");
		LOGGER.log(Level.INFO, "Submitting query.. #query = " + queries.size());
		List<List<QueryResult>> allResults = new ArrayList<List<QueryResult>>();
		// for (int i = 0; i < 32; i++) {
		// Map<String, Float> fieldToBoost = new HashMap<String, Float>();
		// fieldToBoost.put(ImdbIndexer.TITLE_ATTRIB, i % 2 + 1.0f);
		// fieldToBoost.put(ImdbIndexer.KEYWORDS_ATTRIB, (i / 2) % 2 + 1.0f);
		// fieldToBoost.put(ImdbIndexer.PLOT_ATTRIB, (i / 4) % 2 + 1.0f);
		// fieldToBoost.put(ImdbIndexer.ACTORS_ATTRIB, (i / 8) % 2 + 1.0f);
		// fieldToBoost.put(ImdbIndexer.REST_ATTRIB, (i / 16) % 2 + 1.0f);
		// LOGGER.log(Level.INFO, i + ": " + fieldToBoost.toString());
		// List<QueryResult> results = QueryServices.runQueriesWithBoosting(
		// queries, indexName, new BM25Similarity(), fieldToBoost);
		// allResults.add(results);
		// }

		// for (int i = 0; i < 5; i++) {
		// Map<String, Float> fieldToBoost = new HashMap<String, Float>();
		// fieldToBoost.put(ImdbIndexer.TITLE_ATTRIB, i == 0 ? 1f : 0f);
		// fieldToBoost.put(ImdbIndexer.KEYWORDS_ATTRIB, i == 1 ? 1f : 0f);
		// fieldToBoost.put(ImdbIndexer.PLOT_ATTRIB, i == 2 ? 1f : 0f);
		// fieldToBoost.put(ImdbIndexer.ACTORS_ATTRIB, i == 3 ? 1f : 0f);
		// fieldToBoost.put(ImdbIndexer.REST_ATTRIB, i == 4 ? 1f : 0f);
		// LOGGER.log(Level.INFO, i + ": " + fieldToBoost.toString());
		// List<QueryResult> results = QueryServices.runQueriesWithBoosting(
		// queries, indexName, new BM25Similarity(), fieldToBoost);
		// allResults.add(results);
		// }

		Map<String, Float> fieldToBoost = new HashMap<String, Float>();
		fieldToBoost.put(ImdbIndexer.TITLE_ATTRIB, 1f);
		fieldToBoost.put(ImdbIndexer.KEYWORDS_ATTRIB, 2f);
		fieldToBoost.put(ImdbIndexer.PLOT_ATTRIB, 2f);
		fieldToBoost.put(ImdbIndexer.ACTORS_ATTRIB, 2f);
		fieldToBoost.put(ImdbIndexer.REST_ATTRIB, 2f);
		List<QueryResult> results = QueryServices.runQueriesWithBoosting(queries, indexName, new BM25Similarity(),
				fieldToBoost, 200);
		allResults.add(results);
		fieldToBoost = new HashMap<String, Float>();
		fieldToBoost.put(ImdbIndexer.TITLE_ATTRIB, 0.20f);
		fieldToBoost.put(ImdbIndexer.KEYWORDS_ATTRIB, 0.20f);
		fieldToBoost.put(ImdbIndexer.PLOT_ATTRIB, 0.23f);
		fieldToBoost.put(ImdbIndexer.ACTORS_ATTRIB, 0.18f);
		fieldToBoost.put(ImdbIndexer.REST_ATTRIB, 0.19f);
		results = QueryServices.runQueriesWithBoosting(queries, indexName, new BM25Similarity(), fieldToBoost, 200);
		allResults.add(results);

		LOGGER.log(Level.INFO, "Writing results to file..");
		try (FileWriter fw = new FileWriter("data/result/param_compare.csv")) {
			for (int i = 0; i < queries.size(); i++) {
				fw.write(allResults.get(0).get(i).query.getText() + ",");
				for (int j = 0; j < allResults.size(); j++) {
					fw.write(allResults.get(j).get(i).precisionAtK(20) + ",");
				}
				fw.write("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		// best params are 1,2,2,2,2
		// best params2 are 0.20, 0.20, 0.23, 0.18, 0.19
		// the comparison shows that first param set has slightly better
		// precision (0.29 vs 0.26)
	}

	public static void expInex(int expNo, int total, float... gamma) {
		// list should be sorted
		List<InexFile> fileList = InexFile.loadInexFileList(ImdbClusterDirectoryInfo.FILE_LIST);
		LOGGER.log(Level.INFO, "Building index..");
		String indexName = ImdbClusterDirectoryInfo.LOCAL_INDEX + "imdb_" + expNo;
		fileList = fileList.subList(0, (fileList.size() * expNo) / total);
		HashMap<String, InexFile> idToInexFile = new HashMap<String, InexFile>();
		for (InexFile file : fileList) {
			idToInexFile.put(FilenameUtils.removeExtension(new File(file.path).getName()), file);
		}
		new ImdbIndexer().buildIndex(fileList, indexName);
		LOGGER.log(Level.INFO, "Loading and running queries..");
		List<ExperimentQuery> queries = QueryServices.loadInexQueries(ImdbClusterDirectoryInfo.QUERY_FILE,
				ImdbClusterDirectoryInfo.QREL_FILE, "title");
		LOGGER.log(Level.INFO, "Number of loaded queries: " + queries.size());
		String queryAttribs[] = { ImdbIndexer.TITLE_ATTRIB, ImdbIndexer.KEYWORDS_ATTRIB, ImdbIndexer.PLOT_ATTRIB,
				ImdbIndexer.ACTORS_ATTRIB };
		List<QueryResult> results = QueryServices.runQueries(queries, indexName, queryAttribs, 200);
		LOGGER.log(Level.INFO, "Writing results to file..");
		try (FileWriter fw = new FileWriter(ImdbClusterDirectoryInfo.RESULT_DIR + "imdb_" + expNo + ".csv")) {
			for (QueryResult mqr : results) {
				fw.write(mqr.toString() + "\n");
			}
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getMessage());
		}
		try {
			LOGGER.log(Level.INFO, "cleanup..");
			FileUtils.deleteDirectory(new File(indexName));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void buildGlobalIndex(int expNo, int total) {
		List<InexFile> fileList = InexFile.loadInexFileList(ImdbClusterDirectoryInfo.FILE_LIST);
		LOGGER.log(Level.INFO, "Building index..");
		String indexName = "/scratch/cluster-share/ghadakcv/data/index/imdb_" + total + "_" + expNo;
		fileList = fileList.subList(0, (fileList.size() * expNo) / total);
		new ImdbIndexer().buildIndex(fileList, indexName);
	}

	public static void expGlobal(int expNo, int total) {
		// list should be sorted
		String indexName = "/scratch/cluster-share/ghadakcv/data/index/imdb_50/imdb_" + total + "_" + expNo;
		LOGGER.log(Level.INFO, "Loading and running queries..");
		List<ExperimentQuery> queries = QueryServices.loadInexQueries(ImdbClusterDirectoryInfo.QUERY_FILE,
				ImdbClusterDirectoryInfo.QREL_FILE, "title");
		LOGGER.log(Level.INFO, "Number of loaded queries: " + queries.size());
		Map<String, Float> fieldToBoost = new HashMap<String, Float>();
		fieldToBoost.put(ImdbIndexer.TITLE_ATTRIB, 1.0f);
		fieldToBoost.put(ImdbIndexer.KEYWORDS_ATTRIB, 2.0f);
		fieldToBoost.put(ImdbIndexer.PLOT_ATTRIB, 2.0f);
		fieldToBoost.put(ImdbIndexer.ACTORS_ATTRIB, 2.0f);
		fieldToBoost.put(ImdbIndexer.REST_ATTRIB, 2.0f);
		List<QueryResult> results = QueryServices.runQueriesWithBoosting(queries, indexName, new ClassicSimilarity(),
				fieldToBoost, 200);
		LOGGER.log(Level.INFO, "Writing results to file..");
		try (FileWriter fw = new FileWriter(ImdbClusterDirectoryInfo.RESULT_DIR + "imdb_" + expNo + ".csv");
				FileWriter fw2 = new FileWriter(ImdbClusterDirectoryInfo.RESULT_DIR + "imdb_" + expNo + ".log")) {
			for (QueryResult mqr : results) {
				fw.write(mqr.resultString() + "\n");
			}
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, e.getMessage());
		}
	}
}
