package edu.cmpe202.analyzer.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cmpe202.analyzer.aggregation.Aggregator;
import edu.cmpe202.analyzer.aggregation.SensorAggregator;
import edu.cmpe202.analyzer.classification.RecordClassifier;
import edu.cmpe202.analyzer.classification.RecordHandler;
import edu.cmpe202.analyzer.classification.SensorClassifier;
import edu.cmpe202.analyzer.model.EventRecord;
import edu.cmpe202.analyzer.output.JsonOutputWriter;
import edu.cmpe202.analyzer.output.Report;
import edu.cmpe202.analyzer.parsing.InputParser;
import edu.cmpe202.analyzer.parsing.KeyValueParser;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Small test-only extensions demonstrate that the production pipeline need not change. */
class ExtensibilityTest {
    @TempDir Path temp;

    @Test
    void addsANewRecordTypeByComposingAnotherHandler() throws IOException {
        RecordClassifier<NetworkLog> classifier = fields -> {
            if (!fields.keySet().containsAll(List.of("bytes_in", "bytes_out", "interface"))) {
                return Optional.empty();
            }
            try {
                return Optional.of(new NetworkLog(new BigInteger(fields.get("bytes_in")),
                        new BigInteger(fields.get("bytes_out")), fields.get("interface")));
            } catch (NumberFormatException exception) {
                return Optional.empty();
            }
        };
        var network = new RecordHandler<>("network", classifier, new NetworkAggregator(), null);
        var sensors = new RecordHandler<>("sensors", new SensorClassifier(), new SensorAggregator(), network);
        Path input = Files.writeString(temp.resolve("events.txt"),
                "ts=t bytes_in=100 bytes_out=250 interface=eth0\nts=t sensor=s reading=1\n");
        new Analyzer(new KeyValueParser(), sensors, new JsonOutputWriter()).analyze(input, temp.resolve("output"));
        var output = new ObjectMapper().readTree(temp.resolve("output/network.json").toFile());
        assertEquals(350, output.get("total_bytes").asInt());
        assertTrue(Files.exists(temp.resolve("output/sensors.json")));
    }

    @Test
    void substitutesAJsonLinesParserWithoutChangingClassificationOrAggregation() throws IOException {
        var mapper = new ObjectMapper();
        InputParser jsonLines = line -> {
            try {
                return Optional.of(mapper.readValue(line, new TypeReference<Map<String, String>>() {}));
            } catch (JsonProcessingException exception) {
                return Optional.empty();
            }
        };
        Path input = Files.writeString(temp.resolve("events.jsonl"),
                "{\"sensor\":\"s\",\"reading\":\"7\"}\ninvalid\n{\"sensor\":\"s\",\"reading\":\"9\"}\n");
        var handler = new RecordHandler<>("sensors", new SensorClassifier(), new SensorAggregator(), null);
        new Analyzer(jsonLines, handler, new JsonOutputWriter()).analyze(input, temp.resolve("output"));
        var output = mapper.readTree(temp.resolve("output/sensors.json").toFile());
        assertEquals(2, output.get("s").get("count").asInt());
        assertEquals(8, output.get("s").get("average").asInt());
    }

    @Test
    void substitutesAnOutputStrategyWithoutChangingThePipeline() throws IOException {
        var captured = new AtomicReference<List<Report>>();
        Path input = Files.writeString(temp.resolve("events.txt"), "ts=t sensor=s reading=7\n");
        var handler = new RecordHandler<>("sensors", new SensorClassifier(), new SensorAggregator(), null);
        new Analyzer(new KeyValueParser(), handler, (directory, reports) -> captured.set(reports))
                .analyze(input, temp.resolve("unused"));
        assertEquals("sensors", captured.get().get(0).name());
        assertTrue(captured.get().get(0).data().containsKey("s"));
        assertFalse(Files.exists(temp.resolve("unused")));
    }

    private record NetworkLog(BigInteger bytesIn, BigInteger bytesOut, String networkInterface)
            implements EventRecord {
    }

    private static final class NetworkAggregator implements Aggregator<NetworkLog> {
        private BigInteger total = BigInteger.ZERO;

        public void accept(NetworkLog record) {
            total = total.add(record.bytesIn()).add(record.bytesOut());
        }

        public Map<String, Object> snapshot() {
            return Map.of("total_bytes", total);
        }
    }
}

