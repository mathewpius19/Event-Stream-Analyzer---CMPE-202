package edu.cmpe202.analyzer.parsing;

import java.util.Map;
import java.util.Optional;

/** Strategy: turn a complete input line into fields, or reject it silently. */
@FunctionalInterface
public interface InputParser {
    Optional<Map<String, String>> parse(String line);
}

