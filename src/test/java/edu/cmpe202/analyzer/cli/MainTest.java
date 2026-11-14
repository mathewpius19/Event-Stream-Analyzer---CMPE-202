package edu.cmpe202.analyzer.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class MainTest {
    @TempDir Path temp;

    @Test
    void missingFileOptionExplainsUsageAndReturnsNonzero() {
        var result = run();
        assertEquals(2, result.status());
        assertTrue(result.error().contains("Missing required --file"));
        assertTrue(result.error().contains("Usage:"));
        assertEquals("", result.output());
    }

    @Test
    void nonexistentInputReturnsNonzeroWithUsefulMessage() {
        var result = run("--file", temp.resolve("missing.txt").toString());
        assertEquals(1, result.status());
        assertTrue(result.error().contains("does not exist"));
    }

    @Test
    void directoryInputIsRejected() {
        assertEquals(1, run("--file", temp.toString()).status());
    }

    @ParameterizedTest
    @MethodSource("invalidArguments")
    void rejectsMissingValuesUnknownAndDuplicateOptions(String[] arguments) {
        var result = run(arguments);
        assertEquals(2, result.status());
        assertTrue(result.error().startsWith("Error:"));
    }

    static Stream<Arguments> invalidArguments() {
        return Stream.of(new String[]{"--file"}, new String[]{"--outdir"},
                new String[]{"--file", "--outdir", "results"}, new String[]{"--bogus", "x"},
                new String[]{"--file", "a", "--file", "b"},
                new String[]{"--file", "a", "--outdir", "b", "--outdir", "c"},
                new String[]{"--file", ""}, new String[]{"a.txt"})
                .map(arguments -> Arguments.of((Object) arguments));
    }

    @Test
    void defaultsToCurrentDirectoryAndAcceptsOptionReordering() {
        assertEquals(Path.of("."), CliOptions.parse(new String[]{"--file", "events.txt"}).outdir());
        var options = CliOptions.parse(new String[]{"--outdir", "results", "--file", "events.txt"});
        assertEquals(Path.of("results"), options.outdir());
        assertEquals(Path.of("events.txt"), options.file());
    }

    @Test
    void offersHelpWithoutRequiringAFile() {
        var result = run("--help");
        assertEquals(0, result.status());
        assertTrue(result.output().contains("Usage:"));
        assertEquals("", result.error());
    }

    @Test
    void createsAllOutputsAndSilentlySkipsMalformedLines() throws IOException {
        Path input = Files.writeString(temp.resolve("input with spaces.txt"), """
                ts=t sensor=s reading=abc
                broken data
                ts=t sensor=s reading=10
                ts=t sensor=s reading=NaN
                ts=t sensor=s reading=20
                """);
        Path output = temp.resolve("new results");
        var result = run("--file", input.toString(), "--outdir", output.toString());
        assertEquals(0, result.status());
        assertEquals("", result.output());
        assertEquals("", result.error());
        var sensors = new ObjectMapper().readTree(output.resolve("sensors.json").toFile());
        assertEquals(2, sensors.get("s").get("count").asInt());
        assertEquals(15, sensors.get("s").get("average").asInt());
        assertEquals("{}\n", Files.readString(output.resolve("devices.json")));
        assertEquals("{}\n", Files.readString(output.resolve("access.json")));
    }

    @Test
    void emptyAndFullyInvalidInputsProduceThreeEmptyObjects() throws IOException {
        for (String contents : new String[]{"", "corrupted\nts=t reading=abc\n"}) {
            Path input = Files.writeString(temp.resolve("input.txt"), contents);
            Path output = temp.resolve("results");
            assertEquals(0, run("--file", input.toString(), "--outdir", output.toString()).status());
            for (String name : new String[]{"sensors", "devices", "access"}) {
                assertEquals("{}\n", Files.readString(output.resolve(name + ".json")));
            }
        }
    }

    @Test
    void unwritableOutputShapeReturnsNonzeroWithoutStackTrace() throws IOException {
        Path input = Files.writeString(temp.resolve("input.txt"), "ts=t sensor=s reading=1\n");
        Path output = Files.writeString(temp.resolve("not-a-directory"), "content");
        var result = run("--file", input.toString(), "--outdir", output.toString());
        assertEquals(1, result.status());
        assertTrue(result.error().contains("Unable to process"));
        assertFalse(result.error().contains("\tat "));
    }

    private Result run(String... arguments) {
        var output = new ByteArrayOutputStream();
        var error = new ByteArrayOutputStream();
        int status;
        try (var out = new PrintStream(output, true, StandardCharsets.UTF_8);
             var err = new PrintStream(error, true, StandardCharsets.UTF_8)) {
            status = Main.run(arguments, out, err);
        }
        return new Result(status, output.toString(StandardCharsets.UTF_8), error.toString(StandardCharsets.UTF_8));
    }

    private record Result(int status, String output, String error) {
    }
}

