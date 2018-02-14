package wiki13.maple;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;

import query.ExperimentQuery;
import query.QueryResult;
import query.QueryServices;
import wiki13.WikiExperimentHelper;

public class WikiMapleCachingExperiment {

	private static final Logger LOGGER = Logger.getLogger(WikiMapleCachingExperiment.class.getName());

	public static void main(String[] args) {
		Options options = new Options();
		Option queryOption = new Option("query", false, "run querying");
		options.addOption(queryOption);
		Option timingOption = new Option("timing", false, "run timig experiment");
		options.addOption(timingOption);
		Option timingSingleOption = new Option("single", true, "run timig experiment");
		options.addOption(timingSingleOption);
		Option useMsnOption = new Option("msn", false, "specifies the query log (msn/inex)");
		options.addOption(useMsnOption);
		Option partitionsOption = new Option("total", true, "number of partitions");
		options.addOption(partitionsOption);
		Option gammaOption = new Option("gamma", true, "the weight of title field");
		options.addOption(gammaOption);
		Option boostDocs = new Option("boost", false, "boost documents using their weights");
		options.addOption(boostDocs);
		CommandLineParser clp = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cl;
		try {
			String indexDirPath = WikiMaplePaths.INDEX_BASE;
			cl = clp.parse(options, args);
			int partitionCount = Integer.parseInt(cl.getOptionValue("total", "100"));
			List<ExperimentQuery> queries;
			float gamma = Float.parseFloat(cl.getOptionValue("gamma", "0.15f"));
			if (cl.hasOption("msn")) {
				queries = QueryServices.loadMsnQueries(WikiMaplePaths.MSN_QUERY_FILE_PATH,
						WikiMaplePaths.MSN_QREL_FILE_PATH);
			} else {
				queries = QueryServices.loadInexQueries(WikiMaplePaths.QUERY_FILE_PATH, WikiMaplePaths.QREL_FILE_PATH,
						"title");
			}
			if (cl.hasOption("query")) {
				for (int expNo = 1; expNo <= partitionCount; expNo++) {
					String indexPath = indexDirPath + expNo;
					List<QueryResult> results;
					long startTime = System.currentTimeMillis();
					if (cl.hasOption("boost")) {
						results = WikiExperimentHelper.runQueriesOnGlobalIndex(indexPath, queries, gamma, true);
					} else {
						results = WikiExperimentHelper.runQueriesOnGlobalIndex(indexPath, queries, gamma);
					}
					long spentTime = System.currentTimeMillis() - startTime;
					LOGGER.log(Level.INFO,
							"Time spent on querying " + queries.size() + " queries is " + spentTime + " seconds");
					WikiExperimentHelper.writeQueryResultsToFile(results, "result/", expNo + ".csv");
				}
			} else if (cl.hasOption("timing")) {
				if (cl.hasOption("msn")) {
					queries = queries.subList(0, 200);
				}
				double times[] = new double[partitionCount];
				int iterationCount = 10;
				for (int i = 0; i < iterationCount; i++) {
					for (int expNo = 1; expNo <= partitionCount; expNo++) {
						String indexPath = indexDirPath + expNo;
						long startTime = System.currentTimeMillis();
						WikiExperimentHelper.runQueriesOnGlobalIndex(indexPath, queries, gamma);
						long spentTime = System.currentTimeMillis() - startTime;
						times[expNo - 1] += spentTime;
					}
				}
				try (FileWriter fw = new FileWriter("time_results.csv")) {
					for (double l : times) {
						fw.write(l / iterationCount + "\n");
					}
				} catch (IOException e) {
					LOGGER.log(Level.SEVERE, e.getMessage(), e);
				}
			} else if (cl.hasOption("single")) {
				String iter = cl.getOptionValue("single");
				long times[] = new long[partitionCount];
				for (int expNo = 1; expNo <= partitionCount; expNo++) {
					String indexPath = indexDirPath + expNo;
					long startTime = System.currentTimeMillis();
					WikiExperimentHelper.runQueriesOnGlobalIndex(indexPath, queries, gamma);
					long spentTime = System.currentTimeMillis() - startTime;
					times[expNo - 1] = spentTime;
					LOGGER.log(Level.INFO,
							"Time spent on querying " + queries.size() + " queries is " + spentTime + " seconds");
				}
				try (FileWriter fw = new FileWriter("time_results_" + iter + ".csv")) {
					for (Long l : times) {
						fw.write(l + "\n");
					}
				} catch (IOException e) {
					LOGGER.log(Level.SEVERE, e.getMessage(), e);
				}
			}
		} catch (org.apache.commons.cli.ParseException e) {
			LOGGER.log(Level.INFO, e.getMessage());
			formatter.printHelp("", options);
		}
	}

}
