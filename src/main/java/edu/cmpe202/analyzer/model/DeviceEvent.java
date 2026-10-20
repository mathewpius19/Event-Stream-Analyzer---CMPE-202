package edu.cmpe202.analyzer.model;

public record DeviceEvent(String device, Severity severity) implements EventRecord {
}

