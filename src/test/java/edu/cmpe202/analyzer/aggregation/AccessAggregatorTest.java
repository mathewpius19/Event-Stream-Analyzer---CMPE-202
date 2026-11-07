package edu.cmpe202.analyzer.aggregation;

import edu.cmpe202.analyzer.model.AccessRecord;
import edu.cmpe202.analyzer.model.AccessResult;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccessAggregatorTest {
    @Test
    void calculatesNearestRankAndNotEvenSizedMedian() {
        var aggregator = new AccessAggregator();
        for (int time : new int[]{60, 12, 45, 20}) {
            add(aggregator, "u", "A", AccessResult.GRANTED, Integer.toString(time));
        }
        assertEquals(Map.of("min", integer(12), "50_percentile", integer(20),
                "90_percentile", integer(60), "99_percentile", integer(60), "max", integer(60)),
                door(aggregator, "A").get("decision_times"));
    }

    @Test
    void calculatesExactRanksForOneHundredUnsortedDurations() {
        var aggregator = new AccessAggregator();
        for (int time = 100; time >= 1; time--) {
            add(aggregator, "u", "A", AccessResult.GRANTED, Integer.toString(time));
        }
        var times = nested(door(aggregator, "A"), "decision_times");
        assertEquals(integer(50), times.get("50_percentile"));
        assertEquals(integer(90), times.get("90_percentile"));
        assertEquals(integer(99), times.get("99_percentile"));
    }

    @Test
    void handlesSingletonAndLargeIntegerDurationsExactly() {
        var aggregator = new AccessAggregator();
        String value = "922337203685477580812345";
        add(aggregator, "u", "A", AccessResult.DENIED, value);
        var summary = door(aggregator, "A");
        assertTrue(nested(summary, "decision_times").values().stream()
                .allMatch(time -> new BigInteger(value).equals(time)));
        assertEquals(Map.of("GRANTED", 0L, "DENIED", 1L), summary.get("results"));
        assertEquals(List.of(), summary.get("flagged_users"));
    }

    @Test
    void flagsThreeDenialsPerUserPerDoorWithoutResettingOnGrant() {
        var aggregator = new AccessAggregator();
        add(aggregator, "u", "A", AccessResult.DENIED, "30");
        add(aggregator, "u", "A", AccessResult.GRANTED, "12");
        add(aggregator, "u", "A", AccessResult.DENIED, "28");
        add(aggregator, "u", "A", AccessResult.DENIED, "35");
        add(aggregator, "u", "B", AccessResult.DENIED, "8");
        add(aggregator, "u", "B", AccessResult.DENIED, "9");
        assertEquals(List.of("u"), door(aggregator, "A").get("flagged_users"));
        assertEquals(List.of(), door(aggregator, "B").get("flagged_users"));
        assertEquals(Map.of("GRANTED", 1L, "DENIED", 3L), door(aggregator, "A").get("results"));
        assertEquals(Map.of("GRANTED", 0L, "DENIED", 2L), door(aggregator, "B").get("results"));
    }

    @Test
    void flagsEveryQualifyingUserOnceInDeterministicOrder() {
        var aggregator = new AccessAggregator();
        for (int attempt = 0; attempt < 4; attempt++) {
            add(aggregator, "z", "A", AccessResult.DENIED, "10");
            add(aggregator, "a", "A", AccessResult.DENIED, "10");
            add(aggregator, "granted-only", "A", AccessResult.GRANTED, "10");
        }
        assertEquals(List.of("a", "z"), door(aggregator, "A").get("flagged_users"));
    }

    @Test
    void supportsZeroAndSignedIntegersWithoutInventingBounds() {
        var aggregator = new AccessAggregator();
        add(aggregator, "u", "A", AccessResult.GRANTED, "0");
        add(aggregator, "u", "A", AccessResult.GRANTED, "-2");
        assertEquals(integer(-2), nested(door(aggregator, "A"), "decision_times").get("50_percentile"));
        assertEquals(integer(0), nested(door(aggregator, "A"), "decision_times").get("max"));
    }

    @Test
    void returnsEmptyObjectWithoutRecords() {
        assertEquals(Map.of(), new AccessAggregator().snapshot());
    }

    @Test
    void snapshotsRemainIndependentOfFutureRecords() {
        var aggregator = new AccessAggregator();
        add(aggregator, "u", "A", AccessResult.DENIED, "1");
        var before = door(aggregator, "A");
        add(aggregator, "u", "A", AccessResult.GRANTED, "2");
        assertEquals(Map.of("GRANTED", 0L, "DENIED", 1L), before.get("results"));
        assertEquals(Map.of("GRANTED", 1L, "DENIED", 1L), door(aggregator, "A").get("results"));
    }

    private void add(AccessAggregator aggregator, String user, String door, AccessResult result, String duration) {
        aggregator.accept(new AccessRecord(user, door, result, new BigInteger(duration)));
    }

    private BigInteger integer(int value) {
        return BigInteger.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> door(AccessAggregator aggregator, String door) {
        return (Map<String, Object>) aggregator.snapshot().get(door);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> nested(Map<String, Object> map, String key) {
        return (Map<String, Object>) map.get(key);
    }
}

