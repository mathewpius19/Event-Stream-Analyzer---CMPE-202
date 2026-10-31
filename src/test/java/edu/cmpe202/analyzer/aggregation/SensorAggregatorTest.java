package edu.cmpe202.analyzer.aggregation;

import edu.cmpe202.analyzer.model.SensorReading;
import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SensorAggregatorTest {
    @Test
    void computesAllStatisticsUsingPopulationStandardDeviation() {
        var aggregator = readings("temperature", "2", "4", "4", "4", "5", "5", "7", "9");
        var result = statistics(aggregator, "temperature");
        assertEquals(8, result.get("count"));
        assertDecimal("2", result.get("minimum"));
        assertDecimal("4.5", result.get("median"));
        assertDecimal("5", result.get("average"));
        assertDecimal("9", result.get("maximum"));
        assertDecimal("2", result.get("std_dev"));
    }

    @Test
    void calculatesOddMedianAndNegativeValues() {
        var result = statistics(readings("s", "2", "-3", "-1"), "s");
        assertDecimal("-3", result.get("minimum"));
        assertDecimal("-1", result.get("median"));
        assertDecimal("-0.7", result.get("average"));
        assertDecimal("2", result.get("maximum"));
        assertDecimal("2.1", result.get("std_dev"));
    }

    @Test
    void handlesSingletonWithZeroDeviation() {
        var result = statistics(readings("s", "45"), "s");
        assertEquals(1, result.get("count"));
        for (String name : new String[]{"minimum", "median", "average", "maximum"}) {
            assertDecimal("45", result.get(name));
        }
        assertDecimal("0", result.get("std_dev"));
    }

    @Test
    void roundsHalfUpOnlyAfterAggregation() {
        var result = statistics(readings("s", "21.5", "25"), "s");
        assertDecimal("23.3", result.get("median"));
        assertDecimal("23.3", result.get("average"));
        assertDecimal("1.8", result.get("std_dev"));
        assertDecimal("-1.3", statistics(readings("negative", "-1.25"), "negative").get("average"));
        assertDecimal("1.3", statistics(readings("positive", "1.25"), "positive").get("average"));
        assertDecimal("0.1", statistics(readings("small", "0.04", "0.04", "0.14"), "small").get("average"));
    }

    @Test
    void groupsByArbitrarySensorNameIndependentlyOfInputOrder() {
        var aggregator = readings("custom sensor", "10", "20");
        aggregator.accept(new SensorReading("humidity", new BigDecimal("60")));
        assertEquals(2, aggregator.snapshot().size());
        assertEquals(2, statistics(aggregator, "custom sensor").get("count"));
        assertDecimal("60", statistics(aggregator, "humidity").get("average"));
    }

    @Test
    void avoidsCancellationWithLargeOffsetsAndSmallSpread() {
        var result = statistics(readings("s",
                "100000000000000000000000000000000000000000000000000",
                "100000000000000000000000000000000000000000000000001"), "s");
        assertDecimal("0.5", result.get("std_dev"));
        assertDecimal("100000000000000000000000000000000000000000000000000.5", result.get("average"));
    }

    @Test
    void returnsEmptyObjectWithoutReadings() {
        assertTrue(new SensorAggregator().snapshot().isEmpty());
    }

    @Test
    void snapshotsDoNotChangeStateOrPreviouslyReturnedResults() {
        var aggregator = readings("s", "10", "20");
        var first = statistics(aggregator, "s");
        assertEquals(aggregator.snapshot(), aggregator.snapshot());
        aggregator.accept(new SensorReading("s", new BigDecimal("30")));
        assertEquals(2, first.get("count"));
        assertEquals(3, statistics(aggregator, "s").get("count"));
    }

    private SensorAggregator readings(String sensor, String... values) {
        var aggregator = new SensorAggregator();
        for (String value : values) {
            aggregator.accept(new SensorReading(sensor, new BigDecimal(value)));
        }
        return aggregator;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> statistics(SensorAggregator aggregator, String sensor) {
        return (Map<String, Object>) aggregator.snapshot().get(sensor);
    }

    private void assertDecimal(String expected, Object actual) {
        assertEquals(0, new BigDecimal(expected).compareTo((BigDecimal) actual), expected + " vs " + actual);
    }
}

