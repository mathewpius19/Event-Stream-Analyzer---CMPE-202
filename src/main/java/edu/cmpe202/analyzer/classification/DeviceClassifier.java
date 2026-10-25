package edu.cmpe202.analyzer.classification;

import edu.cmpe202.analyzer.model.DeviceEvent;
import edu.cmpe202.analyzer.model.Severity;
import java.util.Map;
import java.util.Optional;

public final class DeviceClassifier implements RecordClassifier<DeviceEvent> {
    @Override
    public Optional<DeviceEvent> classify(Map<String, String> fields) {
        if (!FieldValues.hasNonBlank(fields, "device", "severity")) {
            return Optional.empty();
        }
        try {
            return Optional.of(new DeviceEvent(fields.get("device"), Severity.valueOf(fields.get("severity"))));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}

