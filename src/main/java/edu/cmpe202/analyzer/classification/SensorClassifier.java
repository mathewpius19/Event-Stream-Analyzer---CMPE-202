package edu.cmpe202.analyzer.classification;

import edu.cmpe202.analyzer.model.SensorReading;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

public final class SensorClassifier implements RecordClassifier<SensorReading> {
    @Override
    public Optional<SensorReading> classify(Map<String, String> fields) {
        if (!FieldValues.hasNonBlank(fields, "sensor", "reading")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new SensorReading(fields.get("sensor"), new BigDecimal(fields.get("reading"))));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }
}

