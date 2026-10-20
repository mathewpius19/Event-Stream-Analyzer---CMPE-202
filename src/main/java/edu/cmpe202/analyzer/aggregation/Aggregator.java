package edu.cmpe202.analyzer.aggregation;

import edu.cmpe202.analyzer.model.EventRecord;
import java.util.Map;

/** Strategy for accumulating one typed record and returning an output-neutral snapshot. */
public interface Aggregator<T extends EventRecord> {
    void accept(T record);

    Map<String, Object> snapshot();
}

