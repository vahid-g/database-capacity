package stackoverflow;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import database.DatabaseConnection;
import database.DatabaseType;
import indexing.BiwordAnalyzer;

public class StackIndexerWithVotes {

	public static final String ID_FIELD = "id";

	public static final String BODY_FIELD = "Body";

	public static final String SCORE_FIELD = "Score";

	static final int ANSWERS_S_RECALL_SIZE = 1925204;

	private static double totalExp = 100.0;

	private static final Logger LOGGER = Logger.getLogger(StackIndexerWithVotes.class.getName());

	private IndexWriterConfig config;

	public StackIndexerWithVotes(Analyzer analyzer) {
		config = new IndexWriterConfig(analyzer);
		config.setSimilarity(new BM25Similarity());
		config.setRAMBufferSizeMB(1024);
	}

	public static void main(String[] args) throws SQLException, IOException {
		Options options = new Options();
		Option indexOption = new Option("index", true, "index number");
		indexOption.setRequired(true);
		options.addOption(indexOption);
		options.addOption(new Option("bi", false, "biword index"));
		options.addOption(new Option("rest", false, "rest index"));
		CommandLineParser clp = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cl;
		try {
			cl = clp.parse(options, args);
			StackIndexerWithVotes si = null;
			String indexBasePath = "";
			if (cl.hasOption("bi")) {
				si = new StackIndexerWithVotes(new BiwordAnalyzer());
				indexBasePath = "/data/ghadakcv/stack_index_s_recall_bi/";
			} else {
				si = new StackIndexerWithVotes(new StandardAnalyzer());
				indexBasePath = "/data/ghadakcv/stack_index_s_recall/";
			}
			String indexNumber = cl.getOptionValue("index");
			if (cl.hasOption("rest")) {
				String indexPath = indexBasePath + "c" + indexNumber;
				si.indexRest(indexNumber, indexPath, ANSWERS_S_RECALL_SIZE, "answers_s_recall");
			} else {
				String indexPath = indexBasePath + indexNumber;
				si.indexSubsets(indexNumber, indexPath, ANSWERS_S_RECALL_SIZE, "answers_s_recall");
			}
		} catch (org.apache.commons.cli.ParseException e) {
			LOGGER.log(Level.SEVERE, e.getMessage(), e);
			formatter.printHelp("", options);
			return;
		}
	}

	void indexSubsets(String experimentNumber, String indexPath, int tableSize, String tableName)
			throws SQLException, IOException {
		int limit = (int) (Double.parseDouble(experimentNumber) * tableSize / totalExp);
		LOGGER.log(Level.INFO, "indexing subset..");
		String query = "select Id, Body, Score from stack_overflow." + tableName + " order by Score desc limit " + limit
				+ ";";
		indexTable(indexPath, query);
	}

	void indexRest(String experimentNumber, String indexPath, int tableSize, String tableName)
			throws SQLException, IOException {
		int limit = (int) (tableSize - Double.parseDouble(experimentNumber) * tableSize / totalExp);
		LOGGER.log(Level.INFO, "indexing rest..");
		String query = "select Id, Body, Score from stack_overflow." + tableName + " order by Score asc limit " + limit
				+ ";";
		indexTable(indexPath, query);
	}

	private void indexTable(String indexPath, String query) throws IOException, SQLException {
		LOGGER.log(Level.INFO, "query: {0}", query);
		// setting up database connections
		DatabaseConnection dc = new DatabaseConnection(DatabaseType.STACKOVERFLOW);
		Connection conn = dc.getConnection();
		conn.setAutoCommit(false);
		File indexFile = new File(indexPath);
		if (!indexFile.exists()) {
			indexFile.mkdirs();
		}
		Directory directory = FSDirectory.open(Paths.get(indexFile.getAbsolutePath()));
		// indexing
		LOGGER.log(Level.INFO, "indexing..");
		long start = System.currentTimeMillis();
		try (IndexWriter iwriter = new IndexWriter(directory, config)) {
			try (Statement stmt = conn.createStatement()) {
				stmt.setFetchSize(Integer.MIN_VALUE);
				ResultSet rs = stmt.executeQuery(query);
				while (rs.next()) {
					String id = rs.getString("Id");
					String answer = rs.getString("Body");
					String score = rs.getString("Score");
					Document doc = new Document();
					doc.add(new StoredField(ID_FIELD, id));
					doc.add(new StoredField(SCORE_FIELD, score));
					answer = answer.replaceAll("<[^>]+>", " "); // remove xml tags
					answer = StringEscapeUtils.unescapeHtml4(answer); // convert html encoded characters to unicode
					// answer = answer.replaceAll("[^a-zA-Z0-9'. ]", " ").replaceAll("\\s+", " ");
					doc.add(new TextField(BODY_FIELD, answer, Store.NO));
					iwriter.addDocument(doc);
				}
			} catch (SQLException e) {
				LOGGER.log(Level.SEVERE, e.getMessage(), e);
			}
		}
		long end = System.currentTimeMillis();
		LOGGER.log(Level.INFO, "indexing time: {0} mins", (end - start) / 60000);
		dc.close();
	}

}
