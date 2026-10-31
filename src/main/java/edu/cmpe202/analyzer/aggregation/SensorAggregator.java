package edu.cmpe202.analyzer.aggregation;

import edu.cmpe202.analyzer.model.SensorReading;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public final class SensorAggregator implements Aggregator<SensorReading> {
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private final Map<String, List<BigDecimal>> readings = new TreeMap<>();

    @Override
    public void accept(SensorReading record) {
        readings.computeIfAbsent(record.sensor(), key -> new ArrayList<>()).add(record.reading());
    }

    @Override
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        readings.forEach((sensor, values) -> result.put(sensor, summarize(values)));
        return result;
    }

    private Map<String, Object> summarize(List<BigDecimal> values) {
        List<BigDecimal> sorted = new ArrayList<>(values);
        sorted.sort(Comparator.naturalOrder());
        int size = sorted.size();
        BigDecimal count = BigDecimal.valueOf(size);
        BigDecimal origin = sorted.get(0);
        // Shift before averaging so a large common offset does not erase a small spread.
        BigDecimal offsetSum = BigDecimal.ZERO;
        for (BigDecimal value : sorted) {
            offsetSum = offsetSum.add(value.subtract(origin));
        }
        BigDecimal meanOffset = offsetSum.divide(count, PRECISION);
        BigDecimal average = origin.add(meanOffset);
        BigDecimal squaredDeviationSum = BigDecimal.ZERO;
        for (BigDecimal value : sorted) {
            BigDecimal deviation = value.subtract(origin).subtract(meanOffset);
            squaredDeviationSum = squaredDeviationSum.add(deviation.multiply(deviation, PRECISION), PRECISION);
        }
        BigDecimal standardDeviation = squaredDeviationSum.divide(count, PRECISION).sqrt(PRECISION);
        BigDecimal median = size % 2 == 1 ? sorted.get(size / 2)
                : sorted.get(size / 2 - 1).add(sorted.get(size / 2)).divide(TWO);

        Map<String, Object> statistics = new LinkedHashMap<>();
        statistics.put("count", size);
        statistics.put("minimum", rounded(sorted.get(0)));
        statistics.put("median", rounded(median));
        statistics.put("average", rounded(average));
        statistics.put("maximum", rounded(sorted.get(size - 1)));
        statistics.put("std_dev", rounded(standardDeviation));
        return statistics;
    }

    private BigDecimal rounded(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP).stripTrailingZeros();
    }
}

