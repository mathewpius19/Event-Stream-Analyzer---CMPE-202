package edu.cmpe202.analyzer.output;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** JSON output strategy. Serialization is deliberately independent of record types. */
public final class JsonOutputWriter implements OutputWriter {
    private final ObjectWriter writer;

    public JsonOutputWriter() {
        var factory = JsonFactory.builder().enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN).build();
        var separators = Separators.createDefaultInstance()
                .withObjectFieldValueSpacing(Separators.Spacing.AFTER)
                .withObjectEmptySeparator("")
                .withArrayEmptySeparator("");
        var indentation = new DefaultIndenter("  ", "\n");
        var printer = new DefaultPrettyPrinter().withSeparators(separators)
                .withObjectIndenter(indentation).withArrayIndenter(indentation);
        writer = new ObjectMapper(factory).writer(printer);
    }

    @Override
    public void write(Path directory, List<Report> reports) throws IOException {
        Files.createDirectories(directory);
        for (Report report : reports) {
            String json = writer.writeValueAsString(report.data()) + "\n";
            Files.writeString(directory.resolve(report.name() + ".json"), json, StandardCharsets.UTF_8);
        }
    }
}

