package edu.cmpe202.analyzer.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Failsafe runs these after packaging, so the actual JAR and process exit code are exercised. */
class PackagedCliIT {
    @TempDir Path temp;

    @Test
    void packagedJarDefaultsToItsProcessWorkingDirectory() throws Exception {
        Path input = Files.writeString(temp.resolve("input with spaces.txt"),
                "ts=t sensor=s reading=10\nbad line\nts=t sensor=s reading=20\n");
        var result = execute("--file", input.toString());
        assertEquals(0, result.status());
        assertEquals("", result.text());
        var sensors = new ObjectMapper().readTree(temp.resolve("sensors.json").toFile());
        assertEquals(15, sensors.get("s").get("average").asInt());
        assertEquals("{}\n", Files.readString(temp.resolve("devices.json")));
        assertEquals("{}\n", Files.readString(temp.resolve("access.json")));
    }

    @Test
    void packagedJarReturnsRealNonzeroExitCodesForMissingFileErrors() throws Exception {
        var missingArgument = execute();
        assertEquals(2, missingArgument.status());
        assertTrue(missingArgument.text().contains("--file"));
        var nonexistentFile = execute("--file", temp.resolve("missing.txt").toString());
        assertEquals(1, nonexistentFile.status());
        assertTrue(nonexistentFile.text().contains("does not exist"));
    }

    @Test
    void packagedJarMatchesAllSampleFixturesInAnExplicitOutputDirectory() throws Exception {
        Path sample = Path.of("samples/events.txt").toAbsolutePath();
        Path output = temp.resolve("nested results");
        var result = execute("--file", sample.toString(), "--outdir", output.toString());
        assertEquals(0, result.status());
        assertEquals("", result.text());
        var mapper = new ObjectMapper();
        for (String name : List.of("sensors", "devices", "access")) {
            assertEquals(mapper.readTree(Path.of("samples/expected", name + ".json").toFile()),
                    mapper.readTree(output.resolve(name + ".json").toFile()), name);
        }
    }

    private Result execute(String... arguments) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-jar");
        command.add(Path.of("target/analyzer.jar").toAbsolutePath().toString());
        command.addAll(Arrays.asList(arguments));
        Path log = Files.createTempFile(temp, "process-", ".log");
        Process process = new ProcessBuilder(command).directory(temp.toFile())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "CLI process timed out");
            return new Result(process.exitValue(), Files.readString(log));
        } finally {
            process.destroyForcibly();
        }
    }

    private record Result(int status, String text) {
    }
}

