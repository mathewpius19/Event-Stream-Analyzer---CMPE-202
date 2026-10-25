package edu.cmpe202.analyzer.classification;

import edu.cmpe202.analyzer.aggregation.Aggregator;
import edu.cmpe202.analyzer.model.EventRecord;
import edu.cmpe202.analyzer.output.Report;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chain of Responsibility: handle the first valid match, otherwise delegate.
 * The generic type pairs a classifier with its compatible aggregation strategy.
 */
public final class RecordHandler<T extends EventRecord> {
    private final String reportName;
    private final RecordClassifier<T> classifier;
    private final Aggregator<T> aggregator;
    private final RecordHandler<?> next;

    public RecordHandler(String reportName, RecordClassifier<T> classifier,
                         Aggregator<T> aggregator, RecordHandler<?> next) {
        this.reportName = Objects.requireNonNull(reportName);
        this.classifier = Objects.requireNonNull(classifier);
        this.aggregator = Objects.requireNonNull(aggregator);
        this.next = next;
    }

    public boolean handle(Map<String, String> fields) {
        var record = classifier.classify(fields);
        if (record.isPresent()) {
            aggregator.accept(record.get());
            return true;
        }
        return next != null && next.handle(fields);
    }

    public List<Report> reports() {
        List<Report> reports = new ArrayList<>();
        reports.add(new Report(reportName, aggregator.snapshot()));
        if (next != null) {
            reports.addAll(next.reports());
        }
        return List.copyOf(reports);
    }
}

