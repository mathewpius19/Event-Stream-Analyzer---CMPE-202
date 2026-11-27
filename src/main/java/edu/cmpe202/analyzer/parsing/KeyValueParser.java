package edu.cmpe202.analyzer.parsing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Parses the entire line, never salvaging fields from a corrupted suffix.
 * Double quotes delimit values; escaping inside quoted values is not supported.
 */
public final class KeyValueParser implements InputParser {
    @Override
    public Optional<Map<String, String>> parse(String line) {
        if (line == null || line.isBlank()) {
            return Optional.empty();
        }
        Map<String, String> fields = new LinkedHashMap<>();
        int cursor = 0;
        while (cursor < line.length()) {
            while (cursor < line.length() && Character.isWhitespace(line.charAt(cursor))) {
                cursor++;
            }
            if (cursor == line.length()) {
                break;
            }
            int keyStart = cursor;
            while (cursor < line.length() && line.charAt(cursor) != '=') {
                char character = line.charAt(cursor);
                if (Character.isWhitespace(character) || character == '"') {
                    return Optional.empty();
                }
                cursor++;
            }
            if (cursor == keyStart || cursor == line.length()) {
                return Optional.empty();
            }
            String key = line.substring(keyStart, cursor++);

            // An empty unquoted value (key=) is well formed; classifiers reject blank required values.
            String value;
            if (cursor < line.length() && line.charAt(cursor) == '"') {
                int endQuote = line.indexOf('"', cursor + 1);
                if (endQuote < 0) {
                    return Optional.empty();
                }
                value = line.substring(cursor + 1, endQuote);
                cursor = endQuote + 1;
                if (cursor < line.length() && !Character.isWhitespace(line.charAt(cursor))) {
                    return Optional.empty();
                }
            } else {
                int valueStart = cursor;
                while (cursor < line.length() && !Character.isWhitespace(line.charAt(cursor))) {
                    if (line.charAt(cursor) == '"') {
                        return Optional.empty();
                    }
                    cursor++;
                }
                value = line.substring(valueStart, cursor);
            }
            // A duplicate has no unambiguous meaning; reject rather than overwrite.
            if (fields.putIfAbsent(key, value) != null) {
                return Optional.empty();
            }
        }
        return fields.isEmpty() ? Optional.empty() : Optional.of(Map.copyOf(fields));
    }
}

