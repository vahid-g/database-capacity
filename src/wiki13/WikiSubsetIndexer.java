package wiki13;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import indexing.BiwordAnalyzer;

public class WikiSubsetIndexer {

	public static final Logger LOGGER = Logger.getLogger(WikiSubsetIndexer.class.getName());

	public static void main(String[] args) {
		Options options = new Options();
		Option totalExpNumberOption = new Option("total", true, "Total number of partitions");
		totalExpNumberOption.setRequired(true);
		options.addOption(totalExpNumberOption);
		Option expNumberOption = new Option("exp", true, "Number of partition to index");
		expNumberOption.setRequired(true);
		options.addOption(expNumberOption);
		Option server = new Option("server", true, "Specifies maple/hpc");
		options.addOption(server);
		Option indexPathOption = new Option("path", true, "Specifies output folder");
		indexPathOption.setRequired(true);
		options.addOption(indexPathOption);
		Option comp = new Option("comp", false, "Builds complement index");
		options.addOption(comp);
		Option biword = new Option("bi", false, "Builds biword index");
		options.addOption(biword);
		CommandLineParser clp = new DefaultParser();
		HelpFormatter formatter = new HelpFormatter();
		CommandLine cl;
		try {
			cl = clp.parse(options, args);
			int totalPartitionCount = Integer.parseInt(cl.getOptionValue("total"));
			int partitionNumber = Integer.parseInt(cl.getOptionValue("exp"));
			WikiFilesPaths paths;
			if (cl.getOptionValue("server").equals("hpc")) {
				paths = WikiFilesPaths.getHpcPaths();
			} else if (cl.getOptionValue("server").equals("maple")) {
				paths = WikiFilesPaths.getMaplePaths();
			} else {
				throw new org.apache.commons.cli.ParseException("Server name is not valid");
			}
			String accessCountsFilePath = paths.getAccessCountsPath();
			LOGGER.log(Level.INFO, "Building index for partition {0}/{1}",
					new Object[] { partitionNumber, totalPartitionCount });
			Analyzer analyzer = null;
			if (cl.hasOption("bi")) {
				LOGGER.log(Level.INFO, "Using biword analzer..");
				analyzer = new BiwordAnalyzer();
			} else {
				analyzer = new StandardAnalyzer();
			}
			String indexBase = cl.getOptionValue("path");
			if (indexBase.charAt(indexBase.length() - 1) != '/') {
				indexBase = indexBase + "/";
			}
			long startTime = System.currentTimeMillis();
			if (cl.hasOption("comp")) {
				WikiExperimentHelper.buildComplementIndex(partitionNumber, totalPartitionCount, accessCountsFilePath,
						indexBase + "c" + partitionNumber, analyzer, true);
			} else {
				WikiExperimentHelper.buildGlobalIndex(partitionNumber, totalPartitionCount, accessCountsFilePath,
						indexBase + partitionNumber, analyzer, true);
			}
			long endTime = System.currentTimeMillis();
			LOGGER.log(Level.INFO, "Indexing time: {0} sec", (endTime - startTime) / 1000);
			analyzer.close();
		} catch (org.apache.commons.cli.ParseException e) {
			LOGGER.log(Level.INFO, e.getMessage());
			formatter.printHelp("", options);
			return;
		}
	}
}
