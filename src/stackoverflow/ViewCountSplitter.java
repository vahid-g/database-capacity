package stackoverflow;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

import database.DatabaseConnection;
import database.DatabaseType;

// This class splits the view counts into train/test set by doing sampling without replacement
public class ViewCountSplitter {

	final static int QUESTIONS_A_SIZE = 8034000;
	final static int QUESTIONS_S_SIZE = 1092420;

	private static Logger LOGGER = Logger.getLogger(ViewCountSplitter.class.getName());

	public static void main(String[] args) throws IOException, SQLException {
		String tableName = "questions_s";
		int tableSize = QUESTIONS_S_SIZE;
		sampleWithReplacecment(tableName, tableSize);
	}

	static void sampleWithReplacecment(String tableName, int tableSize) throws IOException, SQLException {
		// read the ids and viewcounts
		DatabaseConnection dc = new DatabaseConnection(DatabaseType.STACKOVERFLOW);
		Connection conn = dc.getConnection();
		int[] ids = new int[tableSize];
		int[] viewcounts = new int[tableSize];
		try (Statement stmt = conn.createStatement()) {
			stmt.setFetchSize(Integer.MIN_VALUE);
			ResultSet rs = stmt.executeQuery("select Id, ViewCount from " + tableName + " order by ViewCount desc");
			int i = 0;
			while (rs.next()) {
				int id = Integer.parseInt(rs.getString("Id"));
				int viewcount = Integer.parseInt(rs.getString("ViewCount"));
				ids[i] = id;
				viewcounts[i] = viewcount;
				i++;
			}
		}
		dc.closeConnection();
		long sum = 0;
		for (int i = 1; i < viewcounts.length; i++) {
			sum += viewcounts[i];
		}
		LOGGER.log(Level.INFO, "sum = " + sum);
		long[] cdf = new long[tableSize];
		cdf[0] = viewcounts[0] / sum;
		LOGGER.log(Level.INFO, "cdf is built");
		// sample
		long sampleSize = sum / 2;
		int[] train = new int[tableSize];
		Random rand = new Random();
		for (int i = 0; i < sampleSize; i++) {
			double r = rand.nextDouble();
			for (int j = 0; j < tableSize; j++) {
				if (r > cdf[j]) {
					train[j]++;
					break;
				}
			}
		}

		try (FileWriter fwTrain = new FileWriter("id_viewcount_train_s1");
				FileWriter fwTest = new FileWriter("id_viewcount_test_s1")) {
			for (int i = 0; i < tableSize; i++) {
				fwTrain.write(ids[i] + "," + train[i] + "\n");
				fwTest.write(ids[i] + "," + viewcounts[i] + "\n");
			}
		}

	}

	static void sampleWithoutReplacement(String tableName, int tableSize) throws IOException, SQLException {
		// read the ids and viewcounts
		DatabaseConnection dc = new DatabaseConnection(DatabaseType.STACKOVERFLOW);
		Connection conn = dc.getConnection();
		int[] ids = new int[tableSize];
		int[] viewcounts = new int[tableSize];
		try (Statement stmt = conn.createStatement()) {
			stmt.setFetchSize(Integer.MIN_VALUE);
			ResultSet rs = stmt.executeQuery("select Id, ViewCount from " + tableName + " order by ViewCount desc");
			int i = 0;
			while (rs.next()) {
				int id = Integer.parseInt(rs.getString("Id"));
				int viewcount = Integer.parseInt(rs.getString("ViewCount"));
				ids[i] = id;
				viewcounts[i] = viewcount;
				i++;
			}
		}
		dc.closeConnection();

		long sum = 0;
		for (int i = 1; i < viewcounts.length; i++) {
			sum += viewcounts[i];
		}
		System.out.println("sum = " + sum);
		// sample
		long sampleSize = sum / 2;
		int[] train = new int[tableSize];
		for (int i = 0; i < sampleSize; i++) {
			long r = ThreadLocalRandom.current().nextLong(sum);
			for (int j = 0; j < viewcounts.length; j++) {
				if (r < viewcounts[j]) {
					train[j]++;
					viewcounts[j]--;
					sum--;
					break;
				}
				r = r - viewcounts[j];
			}
		}

		try (FileWriter fwTrain = new FileWriter("id_viewcount_train");
				FileWriter fwTest = new FileWriter("id_viewcount_test")) {
			for (int i = 0; i < tableSize; i++) {
				fwTrain.write(ids[i] + "," + train[i] + "\n");
				fwTest.write(ids[i] + "," + viewcounts[i] + "\n");
			}
		}
	}
}
