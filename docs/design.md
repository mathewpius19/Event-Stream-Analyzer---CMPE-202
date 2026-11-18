# Event Stream Analyzer

CMPE-202 | Individual assignment | mathewolickal (GitHub: mathewpius19)

Design and implementation review | November 18, 2026

## Problem statement

A smart building produces mixed event lines from sensors, devices, and access
controllers. Operators need a compact summary of measurements, equipment alerts,
and door-access behavior rather than manually reading every event. The program
must turn a text file into three trustworthy JSON reports while continuing past
corrupted or irrelevant lines.

The central design problem is separating changing concerns. Input syntax may
change to JSON Lines; a new event family may introduce different validation and
statistics; a consumer may need CSV. Encoding all of these choices in one parser
or a long type-based conditional would make each extension touch unrelated
behavior. The solution uses typed records, replaceable algorithm interfaces, and
an ordered chain of handlers.

## Boundaries and data flow

**Main** validates CLI arguments with **CliOptions**, requests a fresh session from
**AnalyzerFactory**, and returns useful exit codes. **Analyzer** reads UTF-8 lines
one at a time and asks **InputParser** to parse each complete line. Parsed fields
enter the **RecordHandler<T>** chain. A handler's **RecordClassifier<T>** validates
and creates its typed record; its **Aggregator<T>** receives only valid records.
Handlers later provide format-neutral **Report** snapshots to **OutputWriter**.

The three domain records are **SensorReading**, **DeviceEvent**, and **AccessRecord**,
all implementing **EventRecord**. Their corresponding classifiers and aggregators
have one responsibility each. Jackson is confined to JSON serialization; core
logic uses Java's standard library.

## Pattern 1: Strategy

**Where it appears.** InputParser, Aggregator<T>, and OutputWriter are the strategy
contracts. KeyValueParser, the three concrete aggregators, and JsonOutputWriter
implement them. Analyzer is a context for input/output strategies; each
RecordHandler<T> is a context for its aggregation strategy. The composition root
injects concrete choices.

**Why this fits.** A simpler solution could hard-code key=value parsing and JSON
serialization in the file-processing loop, with static methods for every
aggregation. That would be shorter for one fixed format, but JSON Lines or CSV
would require editing and retesting that loop. Constructor-injected interfaces
let the same orchestration use another format or test double. Typed aggregation
strategies keep each algorithm isolated and prevent mismatched record types at
compile time.

**Benefits and costs.** Strategies improve focused testing and allow format
changes without modifying classification or statistics. The cost is extra
interfaces, classes, constructor wiring, and calls through abstractions.
Format-neutral maps also trade a rigid output DTO schema for flexibility:
misspelled output keys require tests to catch. Aggregators are mutable per-file
strategies, so sessions must not share them across concurrent or unrelated runs.

<!-- PAGE -->

# Classification, consequences, and contracts

## Pattern 2: Chain of Responsibility

**Where it appears.** Each RecordHandler<T> owns a classifier, a compatible
aggregator, a report name, and a reference to the next handler. Its handle method
attempts classification. A successful match updates one aggregator and returns
immediately; a failed match delegates to next. The final handler returns false
when no type matches. AnalyzerFactory wires Sensor -> Device -> Access. The parser
and pipeline do not contain type-specific branches.

**Why this fits.** A simpler if/else chain in Analyzer would perform the same three
checks today, but adding Network Logs would edit the central processing method and
mix validation with dispatch. The handler chain makes precedence explicit in
configuration and lets a new typed pair be inserted without changing existing
classifiers, aggregators, or the processing loop. A broadcast/Observer approach
would deliver an overlapping line to multiple aggregators, contrary to the chosen
one-record classification rule.

**Benefits.** Validation remains local to each record family. A malformed candidate
can fall through to another valid category. The generic handler pairs
RecordClassifier<T> with Aggregator<T> without casts or a central record-type
switch. Ordering and early termination can be tested independently of actual
statistics. Empty reports are collected from every handler, even one that accepted
no records.

**Drawbacks and trade-offs.** Order is semantically significant: an earlier valid
type masks later matches, so adding a handler requires a deliberate precedence
decision and regression tests. An unmatched line traverses all K handlers, O(K),
and the linked implementation uses O(K) call-stack depth. Three handlers make
this cost small; a very large plugin registry might prefer iterative traversal.
The extra objects and indirection make control flow less immediate than a short
if/else. All handlers must be registered in the composition root; implementing
a class alone does not automatically enable it.

## Operational and numerical contracts

- Malformed syntax rejects the complete line before classification. Missing or
  invalid required values silently reject that candidate; extra fields do not
  affect a valid classification.
- Approved overlap order is Sensor -> Device -> Access. Written integer-formatting
  instructions take precedence over conflicting illustrative decimal literals.
  Final decimal statistics use half-up rounding to one decimal, then strip zeros.
- Sensor standard deviation divides by N. An even-sized sensor median averages
  the two middle values. Decimal calculations use DECIMAL128 intermediates and
  shifted deviations to preserve small spreads around large offsets.
- Access percentiles use nearest rank: zero-based index
  ceil(p / 100 * N) - 1. The access p50 intentionally differs from an even-sized
  median. Denial flags accumulate per user and per door across the complete file.
- Exact order statistics retain sensor values and access durations: O(N) stored
  values and O(N log N) worst-case sorting. Raw input lines are not retained.

AnalyzerFactory is a simple composition helper, not an additional claimed GoF
Factory Method pattern. The two principal patterns are Strategy and Chain of
Responsibility.

<!-- PAGE -->

# UML class model: architecture view
<!-- DIAGRAM: architecture.png -->

The class model is split into two readable views. This view shows orchestration,
strategy contracts, the handler's next link, and output snapshots. The concrete
type view on the next page expands the classifier and aggregator implementations.

Solid arrows indicate navigable associations. Dashed arrows indicate dependencies;
a dashed line with a hollow triangle indicates interface realization.
Multiplicity 1 denotes one required collaborator; 0..1 denotes an optional
successor. Generic T is bounded by EventRecord. Pattern roles are labeled on the
corresponding classes/interfaces.

<!-- PAGE -->

# UML class model: concrete type view
<!-- DIAGRAM: record-types.png -->

Each concrete classifier implements RecordClassifier with its matching record
type; each concrete aggregator implements Aggregator with that same type. Java
records expose public accessor methods. All three records realize EventRecord.

The filled diamond denotes composition: AccessAggregator owns its private
DoorStatistics instances. Enum relationships represent typed record properties.
FieldValues is a package-private shared validation utility. Framework classes and
test-only fixtures are omitted. Together, both views cover every production class,
interface, enum, and the private aggregation helper.

<!-- PAGE -->

# Extension plan and verification

## Exact extension changes

**New record type: Network Logs.** Add **NetworkLog implements EventRecord** with
bytesIn, bytesOut, and networkInterface; the latter maps the input key interface,
which is a Java keyword. Add **NetworkLogClassifier implements
RecordClassifier<NetworkLog>** to validate bytes_in, bytes_out, and interface.
Add **NetworkLogAggregator implements Aggregator<NetworkLog>** once the required
network summaries are specified. Register a **RecordHandler<NetworkLog>** named
network in **AnalyzerFactory**, choosing its precedence explicitly. Add classifier,
aggregation, overlap, and output tests. Existing classifiers, aggregators,
RecordHandler, Analyzer, and JsonOutputWriter keep their core logic.

**New input format: JSON Lines.** Add **JsonLinesParser implements InputParser**.
Parse one JSON object per line with Jackson, validate the supported scalar-field
contract, normalize numeric values to strings, and return Optional.empty for
malformed lines. Select this parser in **AnalyzerFactory**. If users must choose a
format at runtime, add a format option to **CliOptions** and selection wiring in
**Main/AnalyzerFactory**. The chain, typed domain objects, aggregators, and writers
remain unchanged; add malformed-JSON and equivalent-output tests.

**New output format: CSV in addition to JSON.** Add **CsvOutputWriter implements
OutputWriter**. Specify one table schema per report, flatten nested sensor/device/
access summaries, define the representation of flagged-user lists, and implement
standard CSV quoting for commas, quotes, and newlines with JDK code. Add
**CompositeOutputWriter** to delegate to both JSON and CSV writers when both are
requested. Select it in **AnalyzerFactory**; optionally extend **CliOptions/Main**
for user selection. Input parsing, the handler chain, and aggregation are
unchanged. Test escaping, empty tables, and JSON/CSV agreement.

These are extension plans rather than shipped CLI features. Network aggregation
semantics and CSV table schemas are identified as new requirements, because the
assignment supplies neither.

## Verification and rubric evidence

**mvn clean verify** passes 92 JUnit 5 cases: 89 Surefire cases and three Failsafe
tests of the actual executable JAR. The suite includes the parser, every
classifier and aggregator, malformed input, empty outputs, exact JSON formatting,
severity ties, nearest-rank boundaries, rounding, CLI errors, and sample fixtures.
Packaged tests verify actual exit statuses and the process's default output path.

**ExtensibilityTest** uses test-only alternate record, parser, and writer
implementations to exercise these contracts without modifying Analyzer. The
supplied 14-line sample has 12 accepted records and two skipped lines. Independent
fixtures check all three complete report objects.

The README contains build/run commands, algorithms, limitations, and output
examples. **grading-checklist.md** maps the full 100-point rubric to source and
tests. **class-diagram.puml** is the editable UML source; this PDF is generated
from **design.md** by **scripts/build_design.py**.

