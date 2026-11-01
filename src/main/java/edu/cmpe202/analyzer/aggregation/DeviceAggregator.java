package edu.cmpe202.analyzer.aggregation;

import edu.cmpe202.analyzer.model.DeviceEvent;
import edu.cmpe202.analyzer.model.Severity;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

public final class DeviceAggregator implements Aggregator<DeviceEvent> {
    private final EnumMap<Severity, Long> severityCounts = emptyCounts();
    private final Map<String, EnumMap<Severity, Long>> deviceCounts = new TreeMap<>();

    @Override
    public void accept(DeviceEvent record) {
        severityCounts.merge(record.severity(), 1L, Long::sum);
        deviceCounts.computeIfAbsent(record.device(), key -> emptyCounts())
                .merge(record.severity(), 1L, Long::sum);
    }

    @Override
    public Map<String, Object> snapshot() {
        if (deviceCounts.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> bySeverity = new LinkedHashMap<>();
        severityCounts.forEach((severity, count) -> bySeverity.put(severity.name(), count));
        Map<String, Object> byDevice = new LinkedHashMap<>();
        deviceCounts.forEach((device, counts) -> {
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            Severity mostFrequent = Severity.CRITICAL;
            long largestCount = -1;
            // Strictly greater preserves the earlier (higher-priority) severity on ties.
            for (Severity severity : Severity.values()) {
                if (counts.get(severity) > largestCount) {
                    largestCount = counts.get(severity);
                    mostFrequent = severity;
                }
            }
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("total", total);
            summary.put("most_frequent_severity", mostFrequent.name());
            byDevice.put(device, summary);
        });
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("by_severity", bySeverity);
        report.put("by_device", byDevice);
        return report;
    }

    private static EnumMap<Severity, Long> emptyCounts() {
        EnumMap<Severity, Long> counts = new EnumMap<>(Severity.class);
        for (Severity severity : Severity.values()) {
            counts.put(severity, 0L);
        }
        return counts;
    }
}

