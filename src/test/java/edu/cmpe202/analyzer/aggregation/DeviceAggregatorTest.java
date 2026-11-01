package edu.cmpe202.analyzer.aggregation;

import edu.cmpe202.analyzer.model.DeviceEvent;
import edu.cmpe202.analyzer.model.Severity;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeviceAggregatorTest {
    @Test
    void countsBySeverityAndDeviceAndChoosesFrequencyBeforePriority() {
        var aggregator = new DeviceAggregator();
        add(aggregator, "thermostat", Severity.INFO, Severity.INFO, Severity.WARN);
        add(aggregator, "lock", Severity.CRITICAL, Severity.ERROR);
        var report = aggregator.snapshot();
        assertEquals(Map.of("CRITICAL", 1L, "ERROR", 1L, "WARN", 1L, "INFO", 2L), report.get("by_severity"));
        assertEquals(Map.of("total", 3L, "most_frequent_severity", "INFO"), device(report, "thermostat"));
        assertEquals(Map.of("total", 2L, "most_frequent_severity", "CRITICAL"), device(report, "lock"));
    }

    @Test
    void resolvesEveryPairwiseTieUsingHighestSeverity() {
        Severity[] priorities = Severity.values();
        for (int high = 0; high < priorities.length; high++) {
            for (int low = high + 1; low < priorities.length; low++) {
                var aggregator = new DeviceAggregator();
                add(aggregator, "d", priorities[low], priorities[high]);
                assertEquals(priorities[high].name(), device(aggregator.snapshot(), "d").get("most_frequent_severity"));
            }
        }
    }

    @Test
    void resolvesFourWayTieToCriticalRegardlessOfEncounterOrder() {
        var aggregator = new DeviceAggregator();
        add(aggregator, "d", Severity.INFO, Severity.WARN, Severity.ERROR, Severity.CRITICAL);
        assertEquals("CRITICAL", device(aggregator.snapshot(), "d").get("most_frequent_severity"));
    }

    @Test
    void includesZeroCountsForUnusedSeverityLevelsWhenNonempty() {
        var aggregator = new DeviceAggregator();
        add(aggregator, "d", Severity.WARN);
        assertEquals(Map.of("CRITICAL", 0L, "ERROR", 0L, "WARN", 1L, "INFO", 0L),
                aggregator.snapshot().get("by_severity"));
    }

    @Test
    void returnsEmptyObjectWithoutEvents() {
        assertEquals(Map.of(), new DeviceAggregator().snapshot());
    }

    @Test
    void previouslyReturnedSnapshotsRemainIndependent() {
        var aggregator = new DeviceAggregator();
        add(aggregator, "d", Severity.INFO);
        var before = aggregator.snapshot();
        add(aggregator, "d", Severity.ERROR);
        assertEquals(1L, device(before, "d").get("total"));
        assertEquals(2L, device(aggregator.snapshot(), "d").get("total"));
    }

    private void add(DeviceAggregator aggregator, String device, Severity... severities) {
        for (Severity severity : severities) {
            aggregator.accept(new DeviceEvent(device, severity));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> device(Map<String, Object> report, String name) {
        return (Map<String, Object>) ((Map<String, Object>) report.get("by_device")).get(name);
    }
}

