package freebase_experiment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

public class FreebaseExperiment {

	static final String JDBC_DRIVER = "com.mysql.jdbc.Driver";
	static String DB_URL = "jdbc:mysql://engr-db.engr.oregonstate.edu:3307/querycapacity";
	static final int MAX_HITS = 100;
	static final String USER = "querycapacity";
	static final String PASS = "13667v";
	static final String INDEX_BASE = "data/freebase_index/";
	static final String resultDir = "data/result/";

	static final String NAME_ATTRIB = "name";
	static final String DESC_ATTRIB = "description";
	static final String FBID_ATTRIB = "fbid";
	static final String PROF_ATTRIB = "profession";
	static final String GENRE_ATTRIB = "genre";
	static final String FREQ_ATTRIB = "frequency";
	static final String SEMANTIC_TYPE_ATTRIB = "semantic_type";

	public static Session session;

	private static void sshConnect() {
		String user = "ghadakcv";
		String password = "Hanh@nolde?";
		String host = "flip.engr.oregonstate.edu";
		int port = 22;
		int localPort = 4321;
		String remoteHost = "engr-db.engr.oregonstate.edu";
		int remotePort = 3307;
		try {
			JSch jsch = new JSch();
			session = jsch.getSession(user, host, port);
			session.setPassword(password);
			session.setConfig("StrictHostKeyChecking", "no");
			System.out.println("Establishing Connection...");
			session.connect();
			System.out.println("SSH Connection established.");
			int assinged_port = session.setPortForwardingL(localPort,
					remoteHost, remotePort);
			System.out.println("localhost:" + assinged_port + " -> "
					+ remoteHost + ":" + remotePort);
			System.out.println("Port Forwarded");
		} catch (JSchException e) {
			e.printStackTrace();
		}
	}

	private static void sshDisconnect() {
		session.disconnect();
		System.out.println("SSH Disconnected");
	}

	public static String sendCommand(String command) {
		StringBuilder outputBuffer = new StringBuilder();

		try {
			Channel channel = session.openChannel("exec");
			((ChannelExec) channel).setCommand(command);
			InputStream commandOutput = channel.getInputStream();
			channel.connect();
			int readByte = commandOutput.read();

			while (readByte != 0xffffffff) {
				outputBuffer.append((char) readByte);
				readByte = commandOutput.read();
			}

			channel.disconnect();
		} catch (IOException ioX) {
			ioX.printStackTrace();
		} catch (JSchException jschX) {
			jschX.printStackTrace();
		}
		return outputBuffer.toString();
	}

	private static void initialize(boolean isRemote) {
		if (isRemote) {
			sshConnect();
			DB_URL = "jdbc:mysql://localhost:4321/querycapacity";
		}
	}

	private static void finilize(boolean isRemote) {
		if (isRemote) {
			sshDisconnect();
		}
	}

	private static Connection getConnection() throws SQLException {
		Properties connectionProps = new Properties();
		connectionProps.put("user", USER);
		connectionProps.put("password", PASS);
		Connection conn = null;
		try {
			conn = DriverManager.getConnection(DB_URL, connectionProps);
			System.out.println("Connected to Database!");
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return conn;
	}

	// creates an inverted index on the given db table and returns the path to
	// index directory
	public static String createIndex(String tableName) {
		System.out.println("Creating Index...");
		String indexPath = INDEX_BASE + tableName + "/";
		// clean up the index directory
		File dirFile = new File(indexPath);
		if (dirFile.exists()) {
			for (File f : dirFile.listFiles()) {
				f.delete();
			}
		} else {
			dirFile.mkdir();
		}
		Statement stmt = null;
		ResultSet rs = null;
		Directory directory = null;
		IndexWriter writer = null;
		try (Connection databaseConnection = getConnection()) {
			// retrieve the tuples to be indexed
			stmt = databaseConnection.createStatement();
			String sql = "select fbid, name, description from " + tableName;
			rs = stmt.executeQuery(sql);
			// creating the index
			directory = FSDirectory.open(Paths.get(indexPath));
			StandardAnalyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			config.setOpenMode(OpenMode.CREATE);
			// .setRAMBufferSizeMB(256.0);
			writer = new IndexWriter(directory, config);
			int count = 0;
			while (rs.next()) {
				count++;
				// System.out.println(rs.getString("name"));
				Document doc = new Document();
				try {
					doc.add(new TextField(NAME_ATTRIB, rs
							.getString(NAME_ATTRIB), Field.Store.YES));
					doc.add(new TextField(DESC_ATTRIB, rs
							.getString(DESC_ATTRIB), Field.Store.YES));
					doc.add(new StoredField(FBID_ATTRIB, rs
							.getString(FBID_ATTRIB)));
					writer.addDocument(doc);
				} catch (IllegalArgumentException e) { // the field is null
					// do nothing
				}
			}
			System.out.println("Indexed documents: " + count);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null)
					rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException se2) {
			}
			try {
				if (writer != null)
					writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				if (directory != null)
					directory.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		System.out.println("Indexing Done!");
		return indexPath;
	}

	public static String buildDataQuery(String tableName, String[] attribs) {
		StringBuilder sb = new StringBuilder();
		sb.append("select fbid");
		for (String attrib : attribs) {
			sb.append(", " + attrib);
		}
		sb.append(" from " + tableName);
		String sql = sb.toString();
		System.out.println(sql);
		return sql;
	}

	public static String buildConditionalDataQuery(String tableName,
			String[] attribs, int lo, int hi) {
		String baseQuery = buildDataQuery(tableName, attribs);
		String sql = baseQuery + " where id > " + lo + " AND id < " + hi;
		return sql;
	}

	public static String createIndexWithQuery(String sqlQuery,
			String[] attribs, String indexPath) {
		System.out.println("Creating Index...");
		// clean up the index directory
		File dirFile = new File(indexPath);
		if (dirFile.exists()) {
			for (File f : dirFile.listFiles()) {
				f.delete();
			}
		} else {
			dirFile.mkdir();
		}
		Statement stmt = null;
		ResultSet rs = null;
		Directory directory = null;
		IndexWriter writer = null;
		try (Connection databaseConnection = getConnection()) {
			// retrieve the tuples to be indexed
			stmt = databaseConnection.createStatement();
			rs = stmt.executeQuery(sqlQuery);
			// creating the index
			directory = FSDirectory.open(Paths.get(indexPath));
			StandardAnalyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			config.setOpenMode(OpenMode.CREATE);
			// .setRAMBufferSizeMB(256.0);
			writer = new IndexWriter(directory, config);
			int count = 0;
			while (rs.next()) {
				count++;
				// System.out.println(rs.getString("name"));
				Document doc = new Document();
				try {
					for (String attrib : attribs) {
						doc.add(new TextField(attrib, rs.getString(attrib),
								Field.Store.YES));
					}
					doc.add(new StoredField(FBID_ATTRIB, rs
							.getString(FBID_ATTRIB)));
					writer.addDocument(doc);
				} catch (IllegalArgumentException e) { // the field is null
					// do nothing
				}
			}
			System.out.println("Indexed documents: " + count);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null)
					rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException se2) {
			}
			try {
				if (writer != null)
					writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				if (directory != null)
					directory.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		System.out.println("Indexing Done!");
		return indexPath;
	}

	/*
	 * public static List<FreebaseQuery> getQueriesByTopic(String topic) {
	 * List<FreebaseQuery> queries = new ArrayList<FreebaseQuery>(); String
	 * sqlQuery = null; sqlQuery =
	 * "select id, fbid, text, frequency from tbl_query where semantic_type like '"
	 * + topic + "'"; // 42422, 42547, 44186 // sqlQuery = //
	 * "select id, fbid, text, frequency from tbl_query where id = 42422";
	 * Statement st = null; ResultSet rs = null; try (Connection conn =
	 * getConnection()) { st = conn.createStatement(); rs =
	 * st.executeQuery(sqlQuery); while (rs.next()) { FreebaseQuery q = new
	 * TableRetrievalService().new FreebaseQuery( rs.getInt("id"),
	 * rs.getString("fbid"), rs.getString( "text").trim(),
	 * rs.getInt("frequency")); queries.add(q); } } catch (SQLException e) {
	 * e.printStackTrace(); } finally { try { if (rs != null) rs.close(); }
	 * catch (SQLException e2) { e2.printStackTrace(); } try { if (st != null)
	 * st.close(); } catch (SQLException e2) { e2.printStackTrace(); } } return
	 * queries; }
	 */

	@Deprecated
	public static String createIndex(String tableName, String[] attribs,
			String indexPath) {
		System.out.println("Creating Index...");
		// clean up the index directory
		File dirFile = new File(indexPath);
		if (dirFile.exists()) {
			for (File f : dirFile.listFiles()) {
				f.delete();
			}
		} else {
			dirFile.mkdir();
		}
		Statement stmt = null;
		ResultSet rs = null;
		Directory directory = null;
		IndexWriter writer = null;
		try (Connection databaseConnection = getConnection()) {
			// retrieve the tuples to be indexed
			stmt = databaseConnection.createStatement();
			StringBuilder sb = new StringBuilder();
			sb.append("select fbid");
			for (String attrib : attribs) {
				sb.append(", " + attrib);
			}
			sb.append(" from " + tableName);
			String sql = sb.toString();
			System.out.println(sql);
			rs = stmt.executeQuery(sql);
			// creating the index
			directory = FSDirectory.open(Paths.get(indexPath));
			StandardAnalyzer analyzer = new StandardAnalyzer();
			IndexWriterConfig config = new IndexWriterConfig(analyzer);
			config.setOpenMode(OpenMode.CREATE);
			// .setRAMBufferSizeMB(256.0);
			writer = new IndexWriter(directory, config);
			int count = 0;
			while (rs.next()) {
				count++;
				// System.out.println(rs.getString("name"));
				Document doc = new Document();
				try {
					for (String attrib : attribs) {
						doc.add(new TextField(attrib, rs.getString(attrib),
								Field.Store.YES));
					}
					doc.add(new StoredField(FBID_ATTRIB, rs
							.getString(FBID_ATTRIB)));
					writer.addDocument(doc);
				} catch (IllegalArgumentException e) { // the field is null
					// do nothing
				}
			}
			System.out.println("Indexed documents: " + count);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null)
					rs.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			try {
				if (stmt != null)
					stmt.close();
			} catch (SQLException se2) {
			}
			try {
				if (writer != null)
					writer.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				if (directory != null)
					directory.close();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		System.out.println("Indexing Done!");
		return indexPath;
	}

	public static List<FreebaseQuery> getQueriesBySqlQuery(String sqlQuery) {
		List<FreebaseQuery> queries = new ArrayList<FreebaseQuery>();
		Statement st = null;
		ResultSet rs = null;
		try (Connection conn = getConnection()) {
			st = conn.createStatement();
			rs = st.executeQuery(sqlQuery);
			while (rs.next()) {
				HashMap<String, String> attribs = new HashMap<String, String>();
				attribs.put(NAME_ATTRIB, rs.getString("text"));
				// attribs.put(DESC_ATTRIB, rs.getString("text"));
				FreebaseQuery q = new FreebaseQuery(rs.getInt("id"), attribs);
				q.text = rs.getString("text").trim().replace(",", " ");
				q.wiki = rs.getString("wiki_id");
				q.frequency = rs.getInt(FREQ_ATTRIB);
				q.fbid = rs.getString(FBID_ATTRIB);
				queries.add(q);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (rs != null)
					rs.close();
			} catch (SQLException e2) {
				e2.printStackTrace();
			}
			try {
				if (st != null)
					st.close();
			} catch (SQLException e2) {
				e2.printStackTrace();
			}
		}
		return queries;
	}

	public static List<FreebaseQuery> buildFreebaseQueriesByRelevancyTable(
			String tableName) {
		String sqlQuery = "select id, wiki_id, fbid, text, frequency from tbl_query as q where q.fbid in (select fbid from "
				+ tableName + ")";
		return getQueriesBySqlQuery(sqlQuery);
	}

	public static void removeKeyword(List<FreebaseQuery> queries, String pattern) {
		Pattern pat = Pattern.compile(pattern);
		for (FreebaseQuery query : queries) {
			// System.out.println(query.text);
			Matcher matcher = pat.matcher(query.text.toLowerCase());
			matcher.find();
			String keyword = matcher.group(0);
			query.attribs.put(NAME_ATTRIB,
					query.text.toLowerCase().replace(keyword, ""));
			// query.attribs.put(DESC_ATTRIB,
			// query.text.toLowerCase().replace(keyword, ""));
		}
	}

	public static void extractAndRemoveKeyword(List<FreebaseQuery> queries,
			String pattern) {
		Pattern pat = Pattern.compile(pattern);
		for (FreebaseQuery query : queries) {
			System.out.println(query.text);
			Matcher matcher = pat.matcher(query.text.toLowerCase());
			matcher.find();
			String keyword = matcher.group(0);
			query.attribs.put(SEMANTIC_TYPE_ATTRIB, keyword);
			query.attribs.put(NAME_ATTRIB,
					query.text.toLowerCase().replace(keyword, ""));
			// query.attribs.put(DESC_ATTRIB,
			// query.text.toLowerCase().replace(keyword, ""));
		}
	}

	private static void addIdenticalAttibuteToQueries(List<FreebaseQuery> list,
			String attrib, String val) {
		for (FreebaseQuery query : list) {
			query.attribs.put(attrib, val);
		}
	}

	public static void runQuery(FreebaseQuery freebaseQuery, String indexPath) {
		IndexReader reader = null;
		try {
			reader = DirectoryReader
					.open(FSDirectory.open(Paths.get(indexPath)));
			IndexSearcher searcher = new IndexSearcher(reader);
			BooleanQuery.Builder builder = new BooleanQuery.Builder();
			for (String attrib : freebaseQuery.attribs.keySet()) {
				builder.add(new QueryParser(attrib, new StandardAnalyzer())
						.parse(QueryParser.escape(freebaseQuery.attribs
								.get(attrib))), BooleanClause.Occur.SHOULD);
			}
			Query query = builder.build();
			// System.out.println("submitting query: " + query.toString());
			TopDocs topDocs = searcher.search(query, MAX_HITS);
			// System.out.println("hits length: " + hits.length);
			for (int i = 0; i < topDocs.scoreDocs.length; i++) {
				Document doc = searcher.doc(topDocs.scoreDocs[i].doc);
				if (doc.get(FreebaseExperiment.FBID_ATTRIB).equals(
						freebaseQuery.fbid)) {
					// System.out.println(searcher.explain(query, hits[i].doc));
					freebaseQuery.relRank = i + 1;
					break;
				}
			}
			int precisionBoundry = topDocs.scoreDocs.length > 10 ? 10
					: topDocs.scoreDocs.length;
			for (int i = 0; i < precisionBoundry; i++) {
				Document doc = searcher.doc(topDocs.scoreDocs[i].doc);
				if (i < 3)
					freebaseQuery.hits[i] = doc.get(NAME_ATTRIB);
			}

		} catch (ParseException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (reader != null)
					reader.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void createSampledTables(String tableName, int tableNumber) {
		Statement st = null;
		String newName = tableName;
		try (Connection conn = getConnection()) {
			st = conn.createStatement();
			for (int i = 1; i <= tableNumber; i++) {
				String sql = "create table " + newName + "_" + i
						+ " as select * from " + tableName + " where mod("
						+ tableName + ".counter, " + tableNumber + ") < " + i
						+ ";";
				st.executeUpdate(sql);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} finally {
			try {
				if (st != null)
					st.close();
			} catch (SQLException e2) {
				e2.printStackTrace();
			}
		}
	}

	public static void experiment_single_table(String tableName, String[] attribs) {
		// runs database size experiment on media table
		// String attribs[] = { "name", "description" };
		List<FreebaseQuery> queries = buildFreebaseQueriesByRelevancyTable(tableName);
		String indexPath = INDEX_BASE + tableName + "/";
		createIndexWithQuery(
				buildDataQuery(tableName, attribs), attribs,
				indexPath);
		FileWriter fw = null;
		try {
			fw = new FileWriter(resultDir + tableName + ".csv");
			for (FreebaseQuery query : queries) {
				runQuery(query, indexPath);
				fw.write(query.id + ", " 
						+ query.text + ", " 
						+ query.frequency
						+ ", " + query.wiki + ", " 
						+ query.p3() + ", "
						+ query.precisionAtK(3) + ", " 
						+ query.mrr() + ","
						+ query.hits[0] + ", " + "\n");
			}
		} catch (Exception e) {
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

	public static void experiment_database_size() {
		// runs database size experiment on media table writing outputs to a
		// single file
		String tableName = "media";
		int mediaTableSize = 3285728;
		String attribs[] = { "name", "description" };
		List<FreebaseQuery> queries = buildFreebaseQueriesByRelevancyTable(tableName);
		String indexPaths[] = new String[10];
		for (int i = 0; i < 10; i++) {
			indexPaths[i] = INDEX_BASE + tableName + "_" + i + "/";
			System.out.println("iteration " + i + "..");
			int lo = (int) ((i / 10.0) * mediaTableSize);
			int hi = (int) (((i + 1) / 10.0) * mediaTableSize);
			createIndexWithQuery(
					buildConditionalDataQuery(tableName, attribs, lo, hi),
					attribs, indexPaths[i]);
		}
		FileWriter fw = null;
		try {
			fw = new FileWriter(resultDir + tableName + "_total.csv");
			for (FreebaseQuery query : queries) {
				fw.write(query.id + ", " + query.text + ", " + query.frequency
						+ ", ");
				for (int i = 0; i < 10; i++) {
					runQuery(query, indexPaths[i]);
					fw.write(query.mrr() + ", ");
				}
				fw.write("\n");
			}
		} catch (Exception e) {
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

	public static void runExperiment2() {
		String tableName = "tbl_tv_program";
		String attribs[] = { NAME_ATTRIB, DESC_ATTRIB };
		String indexPath = INDEX_BASE + tableName + "/";
		// createIndex(tableName, attribs, indexPath);
		String pattern = "program| tv| television| serie| show | show$| film | film$| movie";
		String sql = "select * from query where text REGEXP '" + pattern
				+ "' and fbid in (select fbid from tbl_tv_program);";
		List<FreebaseQuery> queries = getQueriesBySqlQuery(sql);
		Pattern pat = Pattern.compile(pattern);
		for (FreebaseQuery query : queries) {
			System.out.println(query.text);
			Matcher matcher = pat.matcher(query.text.toLowerCase());
			matcher.find();
			String keyword = matcher.group(0);
			query.attribs.put(NAME_ATTRIB,
					query.text.toLowerCase().replace(keyword, ""));
			query.attribs.put(DESC_ATTRIB,
					query.text.toLowerCase().replace(keyword, ""));
		}
		try (FileWriter fw = new FileWriter(resultDir + tableName
				+ "_desc_name q_tvp.csv");) {
			for (FreebaseQuery query : queries) {
				runQuery(query, indexPath);
				fw.write(query.id + ", " + query.text + ", " + query.frequency
						+ ", " + query.wiki + ", " + query.p3() + ", "
						+ query.precisionAtK(20) + ", " + query.mrr() + ","
						+ query.hits[0] + ", " + query.hits[1] + "\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void runExperiment3() {
		String tableName = "tvp_film";
		String attribs[] = { NAME_ATTRIB, DESC_ATTRIB, SEMANTIC_TYPE_ATTRIB };
		String indexPath = INDEX_BASE + tableName + "/";
		// createIndex(tableName, attribs, indexPath);
		String pattern = "program| tv| television| serie| show | show$| film | film$| movie";
		String sql = "select * from query where text REGEXP '" + pattern
				+ "' and fbid in (select fbid from tbl_tv_program);";
		List<FreebaseQuery> queries = getQueriesBySqlQuery(sql);
		Pattern pat = Pattern.compile(pattern);
		for (FreebaseQuery query : queries) {
			System.out.println(query.text);
			Matcher matcher = pat.matcher(query.text.toLowerCase());
			matcher.find();
			String keyword = matcher.group(0);
			query.attribs.put(SEMANTIC_TYPE_ATTRIB, keyword);
			query.attribs.put(NAME_ATTRIB,
					query.text.toLowerCase().replace(keyword, ""));
			query.attribs.put(DESC_ATTRIB,
					query.text.toLowerCase().replace(keyword, ""));
		}
		try (FileWriter fw = new FileWriter(resultDir + tableName
				+ "_desc_name q_tvp.csv");) {
			for (FreebaseQuery query : queries) {
				runQuery(query, indexPath);
				fw.write(query.id + ", " + query.text + ", " + query.frequency
						+ ", " + query.wiki + ", " + query.p3() + ", "
						+ query.precisionAtK(20) + ", " + query.mrr() + ","
						+ query.hits[0] + ", " + query.hits[1] + "\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void experiment_keywordExtraction(String tableName,
			String pattern) {
		String attribs[] = { NAME_ATTRIB, DESC_ATTRIB };
		String indexPath = INDEX_BASE + tableName + "/";
		// createIndex(tableName, attribs, indexPath);
		String sql = "select * from query where text REGEXP '" + pattern
				+ "' and fbid in (select fbid from " + tableName + ");";
		List<FreebaseQuery> queries = getQueriesBySqlQuery(sql);
		removeKeyword(queries, pattern);
		try (FileWriter fw = new FileWriter(resultDir + "t-" + tableName
				+ " q-" + tableName + " a-name" + ".csv");) {
			for (FreebaseQuery query : queries) {
				runQuery(query, indexPath);
				fw.write(query.id + ", " + query.text + ", " + query.wiki
						+ ", " + query.p3() + ", " + query.precisionAtK(10)
						+ ", " + query.precisionAtK(20) + ", " + query.mrr()
						+ "," + query.hits[0] + ", " + query.hits[1] + "\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void experiment_keywordExtraction(String tableName,
			String pattern, String queryTableName) {
		String attribs[] = { NAME_ATTRIB, DESC_ATTRIB, SEMANTIC_TYPE_ATTRIB };
		String indexPath = INDEX_BASE + tableName + "/";
		// createIndex(tableName, attribs, indexPath);
		String sql = "select * from query where text REGEXP '" + pattern
				+ "' and fbid in (select fbid from " + queryTableName + ");";
		List<FreebaseQuery> queries = getQueriesBySqlQuery(sql);
		extractAndRemoveKeyword(queries, pattern);
		try (FileWriter fw = new FileWriter(resultDir + "t-" + tableName
				+ " q-" + queryTableName + " a-name" + ".csv");) {
			for (FreebaseQuery query : queries) {
				runQuery(query, indexPath);
				fw.write(query.id + ", " + query.text + ", " + query.wiki
						+ ", " + query.p3() + ", " + query.precisionAtK(10)
						+ ", " + query.precisionAtK(20) + ", " + query.mrr()
						+ "," + query.hits[0] + ", " + query.hits[1] + "\n");
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {
		boolean isRemote = false;
		/*
		 * initialize(isRemote); String[] table = { "tbl_tv_program",
		 * "tbl_album", "tbl_book" }; String[] pattern = {
		 * "program| tv| television| serie| show | show$| film | film$| movie",
		 * "music|record|song|sound| art |album",
		 * "book|theme|novel|notes|writing|manuscript|story" }; for (int i = 0;
		 * i < table.length; i++) { experiment_keywordExtraction(table[i],
		 * pattern[i]); experiment_keywordExtraction("media", pattern[i],
		 * table[i]); } finilize(isRemote);
		 */
		experiment_database_size();
	}

}