package irstyle;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

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

public class WikiTableIndexer {

	public static String ID_FIELD = "id";

	public static String TEXT_FIELD = "text";

	public static String QREL_FIELD = "qrel";

	Connection conn;

	public WikiTableIndexer(Analyzer analyzer, DatabaseConnection dc) throws IOException, SQLException {

		conn = dc.getConnection();
		conn.setAutoCommit(false);
	}

	public IndexWriterConfig getIndexWriterConfig() {
		IndexWriterConfig config;
		config = new IndexWriterConfig(new StandardAnalyzer());
		config.setSimilarity(new BM25Similarity());
		config.setRAMBufferSizeMB(1024);
		return config;
	}

	public static void main(String[] args) throws IOException, SQLException {
		if (args[0].equals("articles")) {
			WikiTableIndexer.indexArticles();
		} else if (args[0].equals("images")) {
			WikiTableIndexer.indexImages();
		} else if (args[0].equals("links")) {
			WikiTableIndexer.indexLinks();
		} else {
			System.out.println("Wrong input args!");
		}
	}

	public static void indexLinks() throws IOException, SQLException {
		String tableName = "tbl_link_pop";
		try (DatabaseConnection dc = new DatabaseConnection(DatabaseType.WIKIPEDIA)) {
			WikiTableIndexer wti = new WikiTableIndexer(new StandardAnalyzer(), dc);
			for (int i = 1; i <= 100; i += 1) {
				double count = wti.tableSize(tableName);
				int limit = (int) Math.floor((i * count) / 100.0);
				String indexPath = "/data/ghadakcv/wikipedia/" + tableName + "/" + i;
				wti.indexTable(indexPath, tableName, "id", new String[] { "url" }, limit, "pop", "article_id");
			}
		}
	}

	public static void indexImages() throws IOException, SQLException {
		String tableName = "tbl_image_pop";
		try (DatabaseConnection dc = new DatabaseConnection(DatabaseType.WIKIPEDIA)) {
			WikiTableIndexer wti = new WikiTableIndexer(new StandardAnalyzer(), dc);
			for (int i = 1; i <= 100; i += 1) {
				double count = wti.tableSize(tableName);
				int limit = (int) Math.floor((i * count) / 100.0);
				String indexPath = "/data/ghadakcv/wikipedia/" + tableName + "/" + i;
				wti.indexTable(indexPath, tableName, "id", new String[] { "src_tk" }, limit, "pop", "article_id");
			}
		}
	}

	public static void indexArticles() throws IOException, SQLException {
		String tableName = "tbl_article_09";
		try (DatabaseConnection dc = new DatabaseConnection(DatabaseType.WIKIPEDIA)) {
			WikiTableIndexer wti = new WikiTableIndexer(new StandardAnalyzer(), dc);
			for (int i = 1; i <= 100; i += 1) {
				double count = wti.tableSize(tableName);
				int limit = (int) Math.floor((i * count) / 100.0);
				String indexPath = "/data/ghadakcv/wikipedia/" + tableName + "/" + i;
				wti.indexTable(indexPath, tableName, "id", new String[] { "title", "text" }, limit, "popularity", "id");
			}
		}
	}

	private int tableSize(String tableName) throws SQLException {
		int count = -1;
		try (Statement stmt = conn.createStatement()) {
			String sql = "select count(*) count from " + tableName + ";";
			ResultSet rs = stmt.executeQuery(sql);
			while (rs.next()) {
				count = rs.getInt("count");
			}
		}
		return count;
	}

	private void indexTable(String indexPath, String table, String idAttrib, String[] textAttribs, int limit,
			String popularity, String qrelAttrib) throws IOException, SQLException {
		File indexFile = new File(indexPath);
		if (!indexFile.exists()) {
			indexFile.mkdirs();
		}
		Directory directory = FSDirectory.open(Paths.get(indexFile.getAbsolutePath()));
		try (IndexWriter iwriter = new IndexWriter(directory, getIndexWriterConfig())) {
			try (Statement stmt = conn.createStatement()) {
				stmt.setFetchSize(Integer.MIN_VALUE);
				String attribs = idAttrib + "," + qrelAttrib;
				for (String s : textAttribs) {
					attribs += "," + s;
				}
				String sql = "select " + attribs + " from " + table + " order by " + popularity + " desc limit " + limit
						+ ";";
				System.out.println(sql);
				ResultSet rs = stmt.executeQuery(sql);
				while (rs.next()) {
					String id = rs.getString(idAttrib);
					String qrel = rs.getString(qrelAttrib);
					StringBuilder answerBuilder = new StringBuilder();
					for (String s : textAttribs) {
						answerBuilder.append(rs.getString(s));
					}
					String answer = answerBuilder.toString();
					Document doc = new Document();
					doc.add(new StoredField(ID_FIELD, id));
					// answer = StringEscapeUtils.unescapeHtml4(answer); // convert html encoded
					// characters to unicode
					doc.add(new TextField(TEXT_FIELD, answer, Store.NO));
					doc.add(new StoredField(QREL_FIELD, qrel));
					iwriter.addDocument(doc);
				}
			}
		}
	}

}
