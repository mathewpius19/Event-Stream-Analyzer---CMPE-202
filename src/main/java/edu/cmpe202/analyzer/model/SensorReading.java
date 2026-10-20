package edu.cmpe202.analyzer.model;

import java.math.BigDecimal;

public record SensorReading(String sensor, BigDecimal reading) implements EventRecord {
}

