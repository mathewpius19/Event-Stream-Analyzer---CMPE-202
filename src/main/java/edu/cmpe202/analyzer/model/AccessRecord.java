package edu.cmpe202.analyzer.model;

import java.math.BigInteger;

public record AccessRecord(String user, String door, AccessResult result,
                           BigInteger durationMs) implements EventRecord {
}

