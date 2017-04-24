package query;

import java.util.ArrayList;
import java.util.List;

public class QueryResult {

	public ExperimentQuery query;
	public List<String> topResults = new ArrayList<String>();
	public List<String> topResultsTitle = new ArrayList<String>();

	public QueryResult(ExperimentQuery query) {
		this.query = query;
	}

	public double precisionAtK(int k) {
		double count = 0;
		for (int i = 0; i < Math.min(k, topResults.size()); i++) {
			if (query.qrels.contains(topResults.get(i))) {
				count++;
			}
		}
		return count / k;
	}

	public double recallAtK(int k) {
		double count = 0;
		for (int i = 0; i < Math.min(k, topResults.size()); i++) {
			if (query.qrels.contains(topResults.get(i))) {
				count++;
			}
		}
		return count / query.qrels.size();
	}

	double mrr() {
		for (int i = 0; i < topResults.size(); i++) {
			if (query.qrels.contains(topResults.get(i)))
				return (1.0 / (i + 1));
		}
		return 0;
	}

	@Override
	public String toString() {
		return query.text + ", " + precisionAtK(3) + ", " + precisionAtK(10)
				+ ", " + precisionAtK(20) + ", " + mrr() + "," + recallAtK(10)
				+ "," + recallAtK(20) + "," + recallAtK(100);
	}

	public String top10() {
		int limit = topResultsTitle.size() > 10 ? 10 : topResultsTitle.size();
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < limit - 1; i++) {
			sb.append(topResultsTitle.get(i) + ",");
		}
		if (limit > 0)
			sb.append(topResultsTitle.get(limit - 1));
		String resultTuples = sb.toString();
		return query.text + "," + resultTuples;
	}
}
