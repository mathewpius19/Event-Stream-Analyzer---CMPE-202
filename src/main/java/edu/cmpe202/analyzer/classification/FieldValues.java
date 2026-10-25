package edu.cmpe202.analyzer.classification;

import java.util.Map;

/** Shared field-presence validation; numeric and enum rules stay with each classifier. */
final class FieldValues {
    private FieldValues() {
    }

    static boolean hasNonBlank(Map<String, String> fields, String... keys) {
        for (String key : keys) {
            if (!fields.containsKey(key) || fields.get(key) == null || fields.get(key).isBlank()) {
                return false;
            }
        }
        return true;
    }
}

