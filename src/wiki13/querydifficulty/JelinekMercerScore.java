package wiki13.querydifficulty;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

import query.ExperimentQuery;

public class JelinekMercerScore implements QueryDifficultyScoreInterface {

	private static final Logger LOGGER = Logger.getLogger(JelinekMercerScore.class.getName());

	private IndexReader globalIndexReader;

	public JelinekMercerScore(IndexReader globalIndexReader) {
		this.globalIndexReader = globalIndexReader;
	}

	@Override
	public Map<String, Double> computeScore(IndexReader reader, List<ExperimentQuery> queries, String field)
			throws IOException {
		Map<String, Double> difficulties = new HashMap<String, Double>();
		long tfSum = reader.getSumTotalTermFreq(field);
		long globalTfSum = globalIndexReader.getSumTotalTermFreq(field);
		LOGGER.log(Level.INFO, "TF sum:" + tfSum);
		LOGGER.log(Level.INFO, "Global TF sum:" + globalTfSum);
		for (ExperimentQuery query : queries) {
			LOGGER.log(Level.INFO, query.getText());
			try (Analyzer analyzer = new StandardAnalyzer()) {
				TokenStream tokenStream = analyzer.tokenStream(field,
						new StringReader(query.getText().replaceAll("'", "`")));
				CharTermAttribute termAtt = tokenStream.addAttribute(CharTermAttribute.class);
				double p = 1.0;
				try {
					tokenStream.reset();
					while (tokenStream.incrementToken()) {
						String term = termAtt.toString();
						Term currentTokenTerm = new Term(field, term);
						double tf = reader.totalTermFreq(currentTokenTerm);
						double gtf = globalIndexReader.totalTermFreq(currentTokenTerm);
						if (gtf == 0) {
							LOGGER.log(Level.WARNING, "zero gtf for: " + term);
						}
						double probabilityOfTermGivenSubset = tf / tfSum;
						double probabilityOfTermGivenDatabase = gtf / globalTfSum;
						p *= (0.9 * probabilityOfTermGivenSubset + 0.1 * probabilityOfTermGivenDatabase);
						LOGGER.log(Level.INFO, "tf = {0} global tf = {1}", new Object[] { tf, gtf });
						LOGGER.log(Level.INFO, "Pr(term|subset) = " + probabilityOfTermGivenSubset + " Pr(term|db) = "
								+ probabilityOfTermGivenDatabase);
					}
					tokenStream.end();
				} finally {
					tokenStream.close();
				}
				LOGGER.log(Level.INFO, "p = " + p);
				difficulties.put(query.getText(), p);
			}
		}
		return difficulties;
	}

}
