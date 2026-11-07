package edu.cmpe202.analyzer.aggregation;

import edu.cmpe202.analyzer.model.AccessRecord;
import edu.cmpe202.analyzer.model.AccessResult;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class AccessAggregator implements Aggregator<AccessRecord> {
    private final Map<String, DoorStatistics> doors = new TreeMap<>();

    @Override
    public void accept(AccessRecord record) {
        doors.computeIfAbsent(record.door(), key -> new DoorStatistics()).accept(record);
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        doors.forEach((door, statistics) -> result.put(door, statistics.snapshot()));
        return result;
    }

    private static final class DoorStatistics {
        private final List<BigInteger> durations = new ArrayList<>();
        private final EnumMap<AccessResult, Long> results = new EnumMap<>(AccessResult.class);
        private final Map<String, Long> denialsByUser = new TreeMap<>();

        private DoorStatistics() {
            for (AccessResult result : AccessResult.values()) {
                results.put(result, 0L);
            }
        }

        private void accept(AccessRecord record) {
            durations.add(record.durationMs());
            results.merge(record.result(), 1L, Long::sum);
            if (record.result() == AccessResult.DENIED) {
                denialsByUser.merge(record.user(), 1L, Long::sum);
            }
        }

        private Map<String, Object> snapshot() {
            List<BigInteger> sorted = new ArrayList<>(durations);
            sorted.sort(Comparator.naturalOrder());
            Map<String, Object> times = new LinkedHashMap<>();
            times.put("min", sorted.get(0));
            times.put("50_percentile", nearestRank(sorted, 50));
            times.put("90_percentile", nearestRank(sorted, 90));
            times.put("99_percentile", nearestRank(sorted, 99));
            times.put("max", sorted.get(sorted.size() - 1));
            Map<String, Object> resultCounts = new LinkedHashMap<>();
            results.forEach((result, count) -> resultCounts.put(result.name(), count));
            List<String> flaggedUsers = denialsByUser.entrySet().stream()
                    .filter(entry -> entry.getValue() >= 3)
                    .map(Map.Entry::getKey)
                    .toList();
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("decision_times", times);
            summary.put("results", resultCounts);
            summary.put("flagged_users", flaggedUsers);
            return summary;
        }

        private BigInteger nearestRank(List<BigInteger> sorted, int percentile) {
            // ceil(p * N / 100) - 1, using integer arithmetic to avoid boundary rounding.
            int index = (int) (((long) percentile * sorted.size() + 99) / 100) - 1;
            return sorted.get(index);
        }
    }
}

