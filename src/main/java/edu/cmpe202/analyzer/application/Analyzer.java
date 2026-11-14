package edu.cmpe202.analyzer.application;

import edu.cmpe202.analyzer.classification.RecordHandler;
import edu.cmpe202.analyzer.output.OutputWriter;
import edu.cmpe202.analyzer.parsing.InputParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** One-file analysis session. Input is streamed; exact order statistics retain values. */
public final class Analyzer {
    private final InputParser parser;
    private final RecordHandler<?> firstHandler;
    private final OutputWriter writer;

    public Analyzer(InputParser parser, RecordHandler<?> firstHandler, OutputWriter writer) {
        this.parser = Objects.requireNonNull(parser);
        this.firstHandler = Objects.requireNonNull(firstHandler);
        this.writer = Objects.requireNonNull(writer);
    }

    public void analyze(Path input, Path outputDirectory) throws IOException {
        try (var reader = Files.newBufferedReader(input, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                parser.parse(line).ifPresent(firstHandler::handle);
            }
        }
        writer.write(outputDirectory, firstHandler.reports());
    }
}

