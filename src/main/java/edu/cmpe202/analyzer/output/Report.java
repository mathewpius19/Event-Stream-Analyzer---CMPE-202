package edu.cmpe202.analyzer.output;

import java.util.Map;

/** Logical report name without an extension, plus format-neutral JSON-compatible values. */
public record Report(String name, Map<String, Object> data) {
}

