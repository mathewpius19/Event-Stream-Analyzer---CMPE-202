package edu.cmpe202.analyzer.classification;

import edu.cmpe202.analyzer.aggregation.Aggregator;
import edu.cmpe202.analyzer.model.*;
import edu.cmpe202.analyzer.parsing.KeyValueParser;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class ClassificationTest {
    private final KeyValueParser parser = new KeyValueParser();

    @Test
    void acceptsArbitrarySensorNamesAndScientificNotation() {
        var record = new SensorClassifier().classify(Map.of("sensor", "air quality", "reading", "-1.25e2",
                "extra", "ignored")).orElseThrow();
        assertEquals("air quality", record.sensor());
        assertEquals(0, new BigDecimal("-125").compareTo(record.reading()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "NaN", "Infinity", "-Infinity", "0x1.0p2", "1_000", " "})
    void rejectsInvalidSensorNumbers(String value) {
        assertTrue(new SensorClassifier().classify(Map.of("sensor", "s", "reading", value)).isEmpty());
    }

    @Test
    void validatesRequiredFieldsForEveryType() {
        assertTrue(new SensorClassifier().classify(Map.of("reading", "1")).isEmpty());
        assertTrue(new SensorClassifier().classify(Map.of("sensor", "s")).isEmpty());
        assertTrue(new SensorClassifier().classify(Map.of("sensor", "", "reading", "1")).isEmpty());
        assertTrue(new DeviceClassifier().classify(Map.of("device", "d")).isEmpty());
        assertTrue(new DeviceClassifier().classify(Map.of("severity", "INFO")).isEmpty());
        Map<String, String> complete = Map.of("user", "u", "door", "d", "result", "GRANTED", "duration_ms", "1");
        for (String key : complete.keySet()) {
            var missing = new java.util.HashMap<>(complete);
            missing.remove(key);
            assertTrue(new AccessClassifier().classify(missing).isEmpty(), key);
        }
    }

    @Test
    void rejectsBlankRequiredValuesForEveryType() {
        var parser = new edu.cmpe202.analyzer.parsing.KeyValueParser();
        assertTrue(new SensorClassifier().classify(parser.parse("sensor= reading=1").orElseThrow()).isEmpty());
        assertTrue(new SensorClassifier().classify(parser.parse("sensor=s reading=").orElseThrow()).isEmpty());
        assertTrue(new DeviceClassifier().classify(parser.parse("severity=INFO device=").orElseThrow()).isEmpty());
        assertTrue(new AccessClassifier().classify(
                parser.parse("user= door=d result=GRANTED duration_ms=1").orElseThrow()).isEmpty());
        assertTrue(new AccessClassifier().classify(
                parser.parse("user=u door=\"\" result=GRANTED duration_ms=1").orElseThrow()).isEmpty());
    }

    @Test
    void acceptsAllFourSeverityLevelsExactly() {
        for (Severity severity : Severity.values()) {
            assertEquals(severity, new DeviceClassifier().classify(
                    Map.of("device", "d", "severity", severity.name())).orElseThrow().severity());
        }
        assertTrue(new DeviceClassifier().classify(Map.of("device", "d", "severity", "DEBUG")).isEmpty());
        assertTrue(new DeviceClassifier().classify(Map.of("device", "d", "severity", "info")).isEmpty());
    }

    @Test
    void acceptsIntegerDurationsWithoutTruncationOrOverflow() {
        String large = "922337203685477580812345";
        var record = new AccessClassifier().classify(Map.of("user", "u", "door", "d",
                "result", "DENIED", "duration_ms", large)).orElseThrow();
        assertEquals(new BigInteger(large), record.durationMs());
        assertEquals(AccessResult.DENIED, record.result());
        assertEquals(new BigInteger("-2"), new AccessClassifier().classify(
                Map.of("user", "u", "door", "d", "result", "GRANTED", "duration_ms", "-2"))
                .orElseThrow().durationMs());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "1.0", "1e3", "abc", "NaN", " "})
    void rejectsNonIntegerDurations(String duration) {
        assertTrue(new AccessClassifier().classify(Map.of("user", "u", "door", "d",
                "result", "GRANTED", "duration_ms", duration)).isEmpty());
    }

    @Test
    void rejectsInvalidAccessResults() {
        assertTrue(new AccessClassifier().classify(Map.of("user", "u", "door", "d",
                "result", "granted", "duration_ms", "1")).isEmpty());
        assertTrue(new AccessClassifier().classify(Map.of("user", "u", "door", "d",
                "result", "UNKNOWN", "duration_ms", "1")).isEmpty());
    }

    @Test
    void stopsAtFirstValidHandlerForOverlappingRecords() {
        var sensors = new RecordingAggregator<SensorReading>();
        var devices = new RecordingAggregator<DeviceEvent>();
        var access = new RecordingAggregator<AccessRecord>();
        var chain = chain(sensors, devices, access);
        assertTrue(chain.handle(fields("sensor=s reading=3 severity=INFO device=d user=u door=x result=DENIED duration_ms=1")));
        assertEquals(1, sensors.records.size());
        assertTrue(devices.records.isEmpty());
        assertTrue(access.records.isEmpty());
    }

    @Test
    void fallsThroughInvalidEarlierTypesAndStopsAfterDevice() {
        var sensors = new RecordingAggregator<SensorReading>();
        var devices = new RecordingAggregator<DeviceEvent>();
        var access = new RecordingAggregator<AccessRecord>();
        var chain = chain(sensors, devices, access);
        assertTrue(chain.handle(fields("sensor=s reading=abc severity=ERROR device=d user=u door=x result=DENIED duration_ms=1")));
        assertTrue(sensors.records.isEmpty());
        assertEquals(1, devices.records.size());
        assertTrue(access.records.isEmpty());
        assertTrue(chain.handle(fields("severity=BAD device=d user=u door=x result=DENIED duration_ms=1")));
        assertEquals(1, access.records.size());
        assertFalse(chain.handle(Map.of("unknown", "value")));
        assertEquals(List.of("sensors", "devices", "access"),
                chain.reports().stream().map(report -> report.name()).toList());
    }

    private Map<String, String> fields(String line) {
        return parser.parse("ts=t " + line).orElseThrow();
    }

    private RecordHandler<?> chain(RecordingAggregator<SensorReading> sensors,
                                   RecordingAggregator<DeviceEvent> devices,
                                   RecordingAggregator<AccessRecord> access) {
        return new RecordHandler<>("sensors", new SensorClassifier(), sensors,
                new RecordHandler<>("devices", new DeviceClassifier(), devices,
                        new RecordHandler<>("access", new AccessClassifier(), access, null)));
    }

    private static final class RecordingAggregator<T extends EventRecord> implements Aggregator<T> {
        private final List<T> records = new ArrayList<>();

        public void accept(T record) {
            records.add(record);
        }

        public Map<String, Object> snapshot() {
            return Map.of();
        }
    }
}

