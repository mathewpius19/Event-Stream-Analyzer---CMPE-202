# Grading traceability

This table maps the supplied rubric to concrete evidence; it is not a prediction
of the instructor's awarded score.

| Rubric item | Points | Evidence |
| --- | ---: | --- |
| Problem statement | 4 | design.pdf p. 1, own-words problem and constraints |
| At least two patterns and justification | 8 | design.pdf pp. 1-2: Strategy and Chain of Responsibility, compared with simpler alternatives |
| Pattern consequences | 6 | design.pdf pp. 1-2: benefits, indirection/class-count costs, precedence/state trade-offs |
| UML class diagram | 7 | design.pdf pp. 3-4: two views of the same complete model, pattern labels and relationships; editable class-diagram.puml |
| Parsing and classification | 15 | KeyValueParser; SensorClassifier, DeviceClassifier, AccessClassifier; KeyValueParserTest and ClassificationTest |
| All three aggregations | 20 | SensorAggregator, DeviceAggregator, AccessAggregator; corresponding test classes |
| JSON outputs and empty cases | 8 | JsonOutputWriter; JsonOutputWriterTest, MainTest, AnalyzerIntegrationTest, PackagedCliIT |
| Malformed lines and CLI errors | 7 | Full-line parser rejection, classifier validation, Main exit codes; parser/classification/Main/packaged tests |
| Quality and extensibility | 10 | InputParser, Aggregator<T>, OutputWriter strategies; generic RecordHandler chain; AnalyzerFactory; ExtensibilityTest |
| JUnit 5 tests | 15 | 89 Surefire cases plus 3 Failsafe packaged-JAR cases, 92 total |
| Total rubric | 100 | All required areas have implementation/documentation evidence |

Additional required evidence:

- Exact extension plans for Network Logs, JSON Lines, and CSV: design.pdf p. 5.
- Two-space JSON, integer-valued output without decimal points: JsonOutputWriterTest.
- At least 15 distinct tests, including parser, every aggregator, and edge cases:
  see src/test/java and Maven XML reports.
- Nearest-rank percentile formula, population deviation, and median rules: README.
- Build/run instructions and sample results: README and samples/.
- Java 17 target, JUnit 5, only Jackson in production dependencies: pom.xml.

Verified command: `mvn clean verify` under OpenJDK 17.0.18 and Maven 3.9.14,
November 18, 2026. Test reports are regenerated under target/ rather than committed.
