# Location search performance verification

See [REPORT.md](REPORT.md) for measured results and deployment requirements. This harness deletes and recreates synthetic fixtures: **never use an application database**.

Requirements: JDK 21, MySQL 8.0, Connector/J, and a disposable empty copy of the current application schema. Its database name must start with `suntravels_perf_` and end with `_test`; the harness rejects other names. Copy schema only, never development data. The test account needs DDL and fixture privileges in that disposable database and read access to `performance_schema`. Statement history must already be enabled; the harness does not change global server settings.

PowerShell example, from the repository root (supply credentials through environment variables):

```powershell
$env:LOCATION_BENCH_URL='jdbc:mysql://127.0.0.1:3306/suntravels_perf_example_test'
# Set DB_USERNAME and DB_PASSWORD locally; do not commit credentials.
java --class-path '<path-to-mysql-connector-j.jar>' database/benchmarks/LocationSearchBenchmark.java database/benchmarks/results/recheck
```

The default comparison uses original SQL without secondary hotel indexes, then the chosen generated-column indexes and scalar candidate lookup. It seeds 500, 10,000 and 50,000 hotels, one contract and two offerings per hotel. Each query has five warmups and 25 samples. The harness asserts exact result equality, captures EXPLAIN and EXPLAIN ANALYZE, and writes latency, statement rows examined, temporary tables, sorted rows and serialized result bytes. After the first dataset the baseline retains unindexed virtual columns, which its SQL does not reference.

`results/chosen` is the final comparison. `results/functional-experiment` records rejected functional-index variants. `results/final` records the earlier generated-column/EXISTS experiment, which regressed common-destination candidates. The optional second argument `options` reruns exploratory index variants; it is not the final production comparison. Historical exploratory artifacts are retained as evidence.

These are local, warm, sequential JDBC measurements including fetch and small-result serialization, not HTTP or full quotation timings. p50/p95 are nearest-rank samples 13/24 of 25. Statement counters describe the last measured execution. Candidate byte counts cover the candidate ID list only. Rerun on deployment hardware and representative distributions before making capacity claims.
