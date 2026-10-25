package edu.cmpe202.analyzer.classification;

import edu.cmpe202.analyzer.model.AccessRecord;
import edu.cmpe202.analyzer.model.AccessResult;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AccessClassifier implements RecordClassifier<AccessRecord> {
    private static final Pattern INTEGER = Pattern.compile("[+-]?[0-9]+");

    @Override
    public Optional<AccessRecord> classify(Map<String, String> fields) {
        if (!FieldValues.hasNonBlank(fields, "user", "door", "result", "duration_ms")
                || !INTEGER.matcher(fields.get("duration_ms")).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new AccessRecord(fields.get("user"), fields.get("door"),
                    AccessResult.valueOf(fields.get("result")), new BigInteger(fields.get("duration_ms"))));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

