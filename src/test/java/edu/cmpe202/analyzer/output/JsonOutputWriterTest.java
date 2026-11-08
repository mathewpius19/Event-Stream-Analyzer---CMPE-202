package edu.cmpe202.analyzer.output;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JsonOutputWriterTest {
    @TempDir Path temp;

    @Test
    void createsEveryEmptyReportAsAnEmptyObject() throws IOException {
        Path output = temp.resolve("new/nested/directory");
        new JsonOutputWriter().write(output, List.of(new Report("sensors", Map.of()),
                new Report("devices", Map.of()), new Report("access", Map.of())));
        for (String name : List.of("sensors", "devices", "access")) {
            assertEquals("{}\n", Files.readString(output.resolve(name + ".json")));
        }
    }

    @Test
    void prettyPrintsWithTwoSpacesAndWritesIntegerResultsWithoutDecimals() throws IOException {
        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("count", 2);
        statistics.put("average", new BigDecimal("4E+1"));
        statistics.put("std_dev", new BigDecimal("2.5"));
        new JsonOutputWriter().write(temp, List.of(new Report("sensors", Map.of("s", statistics))));
        assertEquals("""
                {
                  "s": {
                    "count": 2,
                    "average": 40,
                    "std_dev": 2.5
                  }
                }
                """, Files.readString(temp.resolve("sensors.json")));
    }

    @Test
    void escapesNamesAndPreservesUnicodeAndArrays() throws IOException {
        String unusualName = "door \"北\" \\ corridor";
        var data = Map.<String, Object>of(unusualName, Map.of("flagged_users", List.of("user one", "two")));
        new JsonOutputWriter().write(temp, List.of(new Report("access", data)));
        var parsed = new ObjectMapper().readTree(temp.resolve("access.json").toFile());
        assertEquals("user one", parsed.get(unusualName).get("flagged_users").get(0).asText());
        assertTrue(Files.readString(temp.resolve("access.json")).contains("      \"user one\""));
    }

    @Test
    void replacesExistingReportInsteadOfAppending() throws IOException {
        Files.writeString(temp.resolve("sensors.json"), "previous content that should disappear");
        new JsonOutputWriter().write(temp, List.of(new Report("sensors", Map.of())));
        assertEquals("{}\n", Files.readString(temp.resolve("sensors.json")));
    }

    @Test
    void surfacesOutputFilesystemErrors() throws IOException {
        Path file = Files.writeString(temp.resolve("file"), "not a directory");
        assertThrows(IOException.class, () ->
                new JsonOutputWriter().write(file, List.of(new Report("sensors", Map.of()))));
    }
}

