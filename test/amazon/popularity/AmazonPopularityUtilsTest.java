package amazon.popularity;

import static org.junit.Assert.assertEquals;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;

import org.junit.Test;

public class AmazonPopularityUtilsTest {

    @Test
    public void testParseUcsdIsbnRatingsData() {
	StringReader sr = new StringReader("x,0000055555,2,2\n"
		+ "y,0000055555,3,2");
	StringWriter sw = new StringWriter();
	AmazonPopularityUtils.parseUcsdIsbnRatingsData(sr, sw);
	assertEquals("555/0000055555.xml,2.5", sw.toString());
    }

    @Test
    public void testloadLtidTotalScoreMap() {
	Map<String, Integer> ltidScoresMap = AmazonPopularityUtils
		.loadLtidTotalScoreMap("data/amazon/queries/inex14sbs.qrels");
	assertEquals(new Integer(31), ltidScoresMap.get("2773690"));
    }

}
