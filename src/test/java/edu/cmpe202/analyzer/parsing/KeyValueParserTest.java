package edu.cmpe202.analyzer.parsing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class KeyValueParserTest {
    private final KeyValueParser parser = new KeyValueParser();

    @Test
    void parsesFieldsAndQuotedSpaces() {
        var fields = parser.parse("ts=t severity=INFO device=thermostat-01 msg=\"Heartbeat OK\"").orElseThrow();
        assertEquals("INFO", fields.get("severity"));
        assertEquals("thermostat-01", fields.get("device"));
        assertEquals("Heartbeat OK", fields.get("msg"));
        assertEquals(4, fields.size());
    }

    @Test
    void preservesEqualsAndUnicodeInsideValues() {
        var fields = parser.parse("ts=t sensor=\"温度 sensor\" reading=2 extra=a=b msg=\"x=y z\"").orElseThrow();
        assertEquals("温度 sensor", fields.get("sensor"));
        assertEquals("a=b", fields.get("extra"));
        assertEquals("x=y z", fields.get("msg"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ts=t sensor=s reading=2 garbage", "ts=t msg=\"unterminated",
        "ts=t msg=\"ok\"trailing", "=missing-key", "sensor =s reading=1",
        "sensor=s reading=2\"x",
        "ts=t ###CORRUPTED LINE### !!!", "ts=t sensor=s reading=1 msg=\"bad\"quote\""
    })
    void rejectsTheWholeMalformedLine(String line) {
        assertTrue(parser.parse(line).isEmpty());
    }

    @Test
    void acceptsEmptyUnquotedValues() {
        var fields = parser.parse("ts=t sensor=s reading=2 note= tag=").orElseThrow();
        assertEquals("", fields.get("note"));
        assertEquals("", fields.get("tag"));
        assertEquals("2", fields.get("reading"));
    }

    @Test
    void rejectsDuplicateKeys() {
        assertTrue(parser.parse("ts=t sensor=a sensor=b reading=1").isEmpty());
        assertTrue(parser.parse("ts=t extra=\"\" extra=x").isEmpty());
    }

    @Test
    void acceptsEmptyQuotedExtraValues() {
        assertEquals("", parser.parse("ts=t msg=\"\"").orElseThrow().get("msg"));
    }

    @Test
    void acceptsLeadingTrailingAndRepeatedWhitespace() {
        var fields = parser.parse("  ts=t\t sensor=s   reading=2  ").orElseThrow();
        assertEquals("2", fields.get("reading"));
        assertEquals(3, fields.size());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t  "})
    void rejectsBlankInput(String line) {
        assertTrue(parser.parse(line).isEmpty());
    }

    @Test
    void leavesTimestampValidationToNeitherParsingNorAggregation() {
        assertEquals("not-a-date", parser.parse("ts=not-a-date sensor=s reading=1").orElseThrow().get("ts"));
        assertTrue(parser.parse("sensor=s reading=1").isPresent());
    }
}

