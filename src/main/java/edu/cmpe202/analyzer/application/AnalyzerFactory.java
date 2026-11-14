package edu.cmpe202.analyzer.application;

import edu.cmpe202.analyzer.aggregation.AccessAggregator;
import edu.cmpe202.analyzer.aggregation.DeviceAggregator;
import edu.cmpe202.analyzer.aggregation.SensorAggregator;
import edu.cmpe202.analyzer.classification.AccessClassifier;
import edu.cmpe202.analyzer.classification.DeviceClassifier;
import edu.cmpe202.analyzer.classification.RecordHandler;
import edu.cmpe202.analyzer.classification.SensorClassifier;
import edu.cmpe202.analyzer.output.JsonOutputWriter;
import edu.cmpe202.analyzer.parsing.KeyValueParser;

/** Composition root: register types and choose format strategies here. */
public final class AnalyzerFactory {
    private AnalyzerFactory() {
    }

    public static Analyzer createDefault() {
        var access = new RecordHandler<>("access", new AccessClassifier(), new AccessAggregator(), null);
        var devices = new RecordHandler<>("devices", new DeviceClassifier(), new DeviceAggregator(), access);
        var sensors = new RecordHandler<>("sensors", new SensorClassifier(), new SensorAggregator(), devices);
        return new Analyzer(new KeyValueParser(), sensors, new JsonOutputWriter());
    }
}

