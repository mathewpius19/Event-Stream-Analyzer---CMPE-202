# Event Stream Analyzer

Java 17 command-line analysis of mixed IoT / smart-building event records.
The application reads a UTF-8 text file, classifies each valid line, and writes
`sensors.json`, `devices.json`, and `access.json`.

## Build and run

Requirements: a **JDK 17 or later** and **Maven 3.9+**. The first build needs
internet access to resolve dependencies. Verify the JDK used by Maven with
`mvn -version`; `maven.compiler.release=17` targets Java 17 bytecode.

```sh
git clone https://github.com/mathewpius19/Event-Stream-Analyzer---CMPE-202.git
cd Event-Stream-Analyzer---CMPE-202
mvn clean verify
java -jar target/analyzer.jar --file samples/events.txt --outdir results
```

For the supplied local checkout, run the same Maven and Java commands from its
`Event-Stream-Analyzer` directory. Quote paths containing spaces.

```sh
java -jar target/analyzer.jar --file "path with spaces/events.txt" --outdir "my results"
java -jar target/analyzer.jar --file samples/events.txt
java -jar target/analyzer.jar --help
```

The second command writes the three files into the **process's current working
directory**. Output directories are created as needed. Existing files with the
three output names are replaced, so select an appropriate output directory.

The shaded JAR includes Jackson; no separate runtime classpath is required.
Python and PlantUML are only needed to regenerate design documents.

## CLI contract

| Argument | Required | Meaning |
| --- | --- | --- |
| `--file <filename.txt>` | Yes | UTF-8 input file |
| `--outdir <directory>` | No | Output directory; defaults to `.` |
| `--help` or `-h` | No | Print usage when supplied alone |

| Exit status | Meaning |
| ---: | --- |
| 0 | Successful processing, including empty input, or help |
| 2 | Missing/invalid/duplicate/unknown command-line arguments |
| 1 | Missing/unreadable input or output/file-system error |

Successful processing writes nothing to stdout or stderr. Invalid individual
records are skipped silently. CLI and I/O errors produce a readable stderr
message and a nonzero exit code.

## Input and classification

Each line contains whitespace-separated `key=value` pairs. Double-quoted values
may contain spaces. Escaped quotes inside quoted values are outside the specified
grammar.

```text
ts=2024-05-10T08:00:00Z sensor=temperature_celsius reading=21.5
ts=2024-05-10T08:00:05Z severity=INFO device=thermostat-01 msg="Heartbeat OK"
ts=2024-05-10T08:00:10Z user=user-42 door=door-A result=DENIED duration_ms=30
```

| Type | Required fields and validation |
| --- | --- |
| Sensor reading | Nonblank `sensor` and numeric `reading` |
| Device event | Nonblank `device` and `severity`: `CRITICAL`, `ERROR`, `WARN`, or `INFO` |
| Access record | Nonblank `user` and `door`, `result`: `GRANTED` or `DENIED`, and integer `duration_ms` |

The parser validates the **whole line**. A corrupted suffix invalidates the line;
a valid prefix is never partially aggregated. Unknown, syntactically valid fields
are ignored by classifiers.

### Explicit decisions for underspecified cases

These decisions make behavior reproducible and are covered by tests.

- **Overlapping types:** first valid classification wins in the order
  **Sensor -> Device -> Access**. If an earlier classifier fails validation, the
  next classifier may accept the line.
- **Numbers:** sensor values use decimal `BigDecimal`, supporting signs, decimal
  fractions, and scientific notation; NaN, infinity, and hexadecimal floating-point
  literals are rejected. Durations use arbitrary-precision `BigInteger` and must
  match `[+-]?[0-9]+`; decimals and exponent notation are rejected.
- **Signed durations:** the PDF specifies an integer without a nonnegative bound,
  so zero and negative integers are accepted.
- **Timestamp:** parsed like any other field but never validated or used.
  Lines without `ts` can still classify, consistent with ignoring that field.
- **Duplicate keys:** reject the entire line because a repeated field is
  ambiguous. Empty extra values (`note=` or `note=""`) are allowed; blank required values are not.
- **Whitespace:** leading/trailing whitespace and repeated spaces or tabs are
  accepted. Enumerated values remain case-sensitive.
- **Integer formatting:** follow the written rule over conflicting illustrative
  literals in the PDF: emit `40`, not `40.0`. Round decimal ties **half-up**.
- **Zero categories:** nonempty device/access reports include zero counts for
  unused severity/result categories. An entirely absent record type is exactly
  an empty object.
- **Order:** grouped names and flagged users are sorted for deterministic output,
  although the assignment does not require JSON key order.

## Aggregations and reproducible numerical rules

### Sensors

Group by the full sensor name, without a fixed whitelist or grouping by device.
Each group contains `count`, `minimum`, `median`, `average`, `maximum`, and
`std_dev`.

- **Median:** sort values; for odd N, choose the middle value; for even N, average
  the two middle values.
- **Average:** sum of the original values divided by N.
- **Population standard deviation:** `sqrt(sum((x - mean)^2) / N)`, dividing by
  **N**, not N-1. A singleton has standard deviation zero.
- **Precision:** decimal input values are retained. Mean offsets, squared
  deviations, division, and square roots use `MathContext.DECIMAL128` (34
  significant digits). Offsets are measured from the minimum before computing
  variance to retain small differences around a large common value.
- **Rounding:** round only final statistics to one decimal with
  `RoundingMode.HALF_UP`, then remove trailing zeros. Counts remain integers.
  Integer-valued decimals are serialized in plain notation.

### Devices

`by_severity` counts each of the four allowed severity levels. `by_device`
contains each device's `total` and `most_frequent_severity`.

Frequency is primary. If frequencies tie, choose the highest severity:
**CRITICAL > ERROR > WARN > INFO**. Two INFO events beat one CRITICAL event;
one INFO and one WARN event yield WARN.

### Access

Group by door. `decision_times` contains `min`, `50_percentile`,
`90_percentile`, `99_percentile`, and `max`.

**Nearest-rank method:** sort N durations and select the value at zero-based index

```text
ceil(p / 100 * N) - 1
```

The implementation uses equivalent integer arithmetic,
`((p * N + 99) / 100) - 1`, for p = 50, 90, 99. It does not interpolate.
For durations `[12, 20, 45, 60]`, p50 is **20**, whereas the median is **32.5**.
This difference is intentional.

`results` counts GRANTED and DENIED. `flagged_users` contains each user with at
least **three denied attempts at that specific door**, once. Counts cover the
entire file; there is no timestamp window, and a grant does not reset denials.

## Outputs and sample verification

All JSON is UTF-8, uses **two-space indentation**, and ends with a newline. All
three files are created even if one or all types are absent; absent types contain
`{}`.

The exact 14-line input from the assignment is in [samples/events.txt](samples/events.txt).
Independently calculated results are in [samples/expected/](samples/expected/).
There are **12 valid records** (3 sensor, 4 device, 5 access) and **2 skipped lines**.

| Sample result | Expected |
| --- | --- |
| Temperature | count 2; min 21.5; median/average 23.3; max 25; std_dev 1.8 |
| Humidity | count 1; min/median/average/max 45; std_dev 0 |
| thermostat-01 | total 2; most frequent severity WARN (tie) |
| door-lock-07 | total 2; most frequent severity CRITICAL (tie) |
| door-A | min 18; p50 28; p90/p99/max 35; GRANTED 1, DENIED 3; user-42 flagged |
| door-B | all decision times 12; GRANTED 1, DENIED 0; no flagged users |

The PDF's earlier JSON blocks illustrate the schema using other values; they are
**not** the expected outputs of its later 14-line sample.

## Architecture and design patterns

```text
CLI -> AnalyzerFactory -> Analyzer
                            |
                     InputParser strategy
                            |
                     RecordHandler chain
                    Sensor -> Device -> Access
                       |         |         |
                    typed aggregation strategies
                            |
                         Reports
                            |
                     OutputWriter strategy
```

**Strategy** separates input syntax, each aggregation algorithm, and output
serialization behind `InputParser`, `Aggregator<T>`, and `OutputWriter`.

**Chain of Responsibility** lives in `RecordHandler<T>`. Each handler validates
one candidate using a typed `RecordClassifier<T>`; it aggregates and stops on
success, or delegates to `next`. Classifiers never update statistics.

`AnalyzerFactory` is the composition root: it chooses concrete strategies and
orders the chain. Its name describes a simple construction helper; it is not
claimed as a third GoF pattern.

Each `Analyzer` is a one-file session with fresh aggregators. Create a new session
for a second file. The default CLI does so automatically.

See [docs/design.pdf](docs/design.pdf) for the problem statement, pattern
justifications, trade-offs, UML, and exact extension steps. The
[editable design source](docs/design.md), [PlantUML source](docs/class-diagram.puml),
[architecture view](docs/architecture.png), and
[concrete type view](docs/record-types.png) are included.

### Extension points

| Change | Add | Modify |
| --- | --- | --- |
| Network Logs | `NetworkLog`, `NetworkLogClassifier`, `NetworkLogAggregator` | Register a handler/report name in `AnalyzerFactory` |
| JSON Lines input | `JsonLinesParser implements InputParser` | Select it in `AnalyzerFactory`; extend `CliOptions` only if exposing a format flag |
| CSV output | `CsvOutputWriter implements OutputWriter`; optionally `CompositeOutputWriter` for both formats | Select writer(s) in `AnalyzerFactory`; optionally add a CLI format flag |

These are hypothetical extensions, not extra CLI features. Production supports
key=value input and JSON output. Test-only prototypes verify the extension
contracts without changing production pipeline logic.

## Tests and grading traceability

```sh
mvn test          # Parser, classifiers, aggregators, writer, CLI, and pipeline tests
mvn clean verify  # All tests plus the packaged-JAR integration checks
```

The verified suite has **92 executed JUnit 5 cases**: **89** in Surefire and **3**
packaged-JAR cases in Failsafe, with zero failures/errors/skips. Parameterized
cases are counted individually. The suite also exceeds 15 distinct test methods.

Coverage includes malformed complete lines, quoted spaces, Unicode, missing fields,
invalid enums/numbers, overlap precedence, population deviation, even/odd medians,
all severity tie pairs, nearest-rank boundaries, per-door flags, empty outputs,
rounding/formatting, filesystem errors, process exit codes, sample fixtures, and
replaceable parser/type/writer implementations.

Results are written to `target/surefire-reports/` and
`target/failsafe-reports/`. [docs/grading-checklist.md](docs/grading-checklist.md)
maps every item in the 100-point rubric to implementation and test evidence.

## Repository layout

```text
pom.xml
src/main/java/edu/cmpe202/analyzer/
  model/           Typed records and enums
  parsing/         InputParser and key=value implementation
  classification/  Validators and RecordHandler chain
  aggregation/     Sensor, device, and access strategies
  output/          Report and JSON output strategy
  application/     Streaming pipeline and composition root
  cli/             Arguments, entry point, and exit codes
src/test/java/edu/cmpe202/analyzer/
docs/              Design PDF, editable sources, UML, and grading checklist
samples/           Assignment sample and independent expected JSON fixtures
scripts/           Optional design-document generator
target/analyzer.jar Generated executable JAR (not tracked)
```

Only Jackson is a third-party production dependency. JUnit is test-scoped.
Core parsing, classification, numerical calculations, and aggregation use Java's
standard library.

## Complexity and limits

The file is read one line at a time; raw lines are not retained. Exact median and
percentiles retain all sensor values and access durations. For N accepted readings
and durations, worst-case memory is O(N), and sorting is O(N log N) across groups.
Device counts and user-denial counts grow with distinct devices and door/user
pairs. Numeric bit lengths also affect arbitrary-precision arithmetic costs.

This is a batch CLI, not an unbounded-memory stream processor. DECIMAL128
intermediate arithmetic is finite precision, and extreme numeric magnitudes,
scales, or input sizes remain constrained by Java memory/BigDecimal limits.
File I/O failures stop the run; output files are written individually, so an I/O
failure partway through output can leave some reports already replaced.

## References

- Course specification: `Event_Stream_Analyzer_Homework_Updated.pdf`.
- [JUnit 5.13.4 user guide](https://docs.junit.org/5.13.4/user-guide/).
- [Maven Shade executable-JAR documentation](https://maven.apache.org/plugins/maven-shade-plugin/examples/executable-jar.html).
- [Jackson 2.20 release documentation](https://github.com/FasterXML/jackson/wiki/Jackson-Release-2.20).
