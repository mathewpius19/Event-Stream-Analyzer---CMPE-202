package edu.cmpe202.analyzer.classification;

import edu.cmpe202.analyzer.model.EventRecord;
import java.util.Map;
import java.util.Optional;

/** Validates one record type; an empty result lets the chain try its next handler. */
@FunctionalInterface
public interface RecordClassifier<T extends EventRecord> {
    Optional<T> classify(Map<String, String> fields);
}

