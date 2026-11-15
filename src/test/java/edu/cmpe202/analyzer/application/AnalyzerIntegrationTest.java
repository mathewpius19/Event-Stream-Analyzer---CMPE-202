package edu.cmpe202.analyzer.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AnalyzerIntegrationTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void matchesIndependentlyCalculatedAssignmentSampleOutputs() throws IOException {
        AnalyzerFactory.createDefault().analyze(Path.of("samples/events.txt"), temp);
        for (String name : List.of("sensors", "devices", "access")) {
            var expected = mapper.readTree(Path.of("samples/expected", name + ".json").toFile());
            var actual = mapper.readTree(temp.resolve(name + ".json").toFile());
            assertEquals(expected, actual, name);
        }
    }

    @Test
    void isolatedSessionsDoNotLeakPreviousAggregations() throws IOException {
        Path input = Files.writeString(temp.resolve("input.txt"), "ts=t sensor=s reading=9\n");
        AnalyzerFactory.createDefault().analyze(input, temp.resolve("first"));
        Files.writeString(input, "");
        AnalyzerFactory.createDefault().analyze(input, temp.resolve("second"));
        assertEquals(1, mapper.readTree(temp.resolve("first/sensors.json").toFile()).get("s").get("count").asInt());
        assertTrue(mapper.readTree(temp.resolve("second/sensors.json").toFile()).isEmpty());
    }

    @Test
    void ignoresInvalidExtraValuesAndKeepsUnicodeGroupNames() throws IOException {
        Path input = Files.writeString(temp.resolve("input.txt"), """
                ts=ignored severity=INFO device="北 lobby" reading=not-a-number extra="a b"
                ts=ignored sensor="air quality" reading=1.25 duration_ms=bad
                """);
        AnalyzerFactory.createDefault().analyze(input, temp.resolve("reports"));
        var devices = mapper.readTree(temp.resolve("reports/devices.json").toFile());
        var sensors = mapper.readTree(temp.resolve("reports/sensors.json").toFile());
        assertEquals(1, devices.get("by_device").get("北 lobby").get("total").asInt());
        assertEquals(1.3, sensors.get("air quality").get("average").asDouble(), 0.0001);
    }
}

