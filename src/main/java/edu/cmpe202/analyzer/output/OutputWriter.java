package edu.cmpe202.analyzer.output;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Strategy: select a serialization format independently of aggregation. */
@FunctionalInterface
public interface OutputWriter {
    void write(Path directory, List<Report> reports) throws IOException;
}

