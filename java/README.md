# RuleScript — Java DSL

RuleScript is an engine-agnostic domain-specific language for developing query rewrite rules.
For details, please see our [paper](https://arxiv.org/abs/2605.05536).

For the Rust/DataFusion backend, see the [root README](../README.md).

A rule is written once, as a `before()` / `after()` pair over RuleScript's relational algebra. From that single definition the repo:

- **generates** a native rule for Apache Calcite,
- **serializes** it to JSON for interpreters inside CockroachDB and Apache Spark, and
- **proves** the before/after pair equivalent with the [QED prover](https://github.com/qed-solver/prover).

The repo currently holds **385 rules**, ported from Calcite, CockroachDB and Spark.

## Build

The project targets Java 25. Build with Maven:

```sh
./mvnw compile -q
```

## Defining a Rule

Rules are Java records implementing `RRule` in `src/main/java/org/qed/RRuleInstances/`. Each provides a `before()` pattern and an `after()`
transformation built from `RelRN` (relational operators) and `RexRN` (row expressions). Every generator and test picks up all files in that
directory automatically, so there is no registry to update.

Example: `FilterMerge`

```java
// src/main/java/org/qed/RRuleInstances/FilterMerge.java
// ORIGIN: Calcite
// SCOPE: FULL
public record FilterMerge() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN inner = source.pred("inner");
    static final RexRN outer = source.pred("outer");

    @Override
    public RelRN before() {
        return source.filter(inner).filter(outer);     // source.filter(P).filter(Q)
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.and(inner, outer)); // source.filter(P AND Q)
    }
}
```

`scan`, `pred` and the other uninterpreted symbols stand for any relation or expression, so one definition covers every concrete instance.

Each rule file carries two header comments:

| Comment | Meaning |
| --- | --- |
| `// ORIGIN:` | The engine the rule comes from: `Calcite` (97 rules), `CockroachDB` (210) or `Spark` (78). |
| `// SCOPE:` | `FULL` if the DSL rule captures the original engine rule completely (42 rules), or `PARTIAL — <what is modeled>` if it covers a restricted instance, such as a fixed arity or only the inner-join variant (343 rules). |

A rule that must hold for several join types can be written once as an `RRuleGenerator` family (see `RRule.java`); `info()` then labels
each instance (for example `{J}=INNER`).

## Layout

```
src/main/java/org/qed/
  RRule.java, RRuleInstance.java   - the rule interface and instance helpers
  RelRN.java, RexRN.java           - the relational / row-expression DSL
  RRuleInstances/                  - the rules (one file each)
  Backends/
    Calcite/                       - code generation into Calcite RelRules
    Cockroach/                     - JSON rules + Go interpreter for CockroachDB
    Spark/                         - JSON rules + Java interpreter for Catalyst
  JSONSerializer, JSONDeserializer, SQLJSONParser, Main, ...
                                   - QED JSON serialization and the SQL/.cos parser
scripts/                           - build, test, and prover scripts (below)
```

## Backends

RuleScript supports three styles of backend integration. Each backend has a `Generated/` directory (one output per rule), a `Tests/`
directory (one test per rule), and its own README with details.

| Backend | Style | Output per rule | Runtime | Details |
| --- | --- | --- | --- | --- |
| Apache Calcite | Code generation | `Generated/<Rule>.java`, a Calcite `RelRule` | `CalciteRuntime.java` helpers | [README](src/main/java/org/qed/Backends/Calcite/README.md) |
| CockroachDB | Interpreted | `Generated/<Rule>.json` | Go package copied into CockroachDB (`Runtime/`, `cockroach.patch`) | [README](src/main/java/org/qed/Backends/Cockroach/README.md) |
| Apache Spark | Interpreted | `Generated/<Rule>.json` | Java interpreter over Catalyst (`Runtime/`) | [README](src/main/java/org/qed/Backends/Spark/README.md) |

The **native runtime backend** (DataFusion) defines and applies rules directly in Rust; see the [root README](../README.md).

### Apache Calcite

```sh
./mvnw -q compile exec:java@calcite-codegen-test                          # all rules
./mvnw -q compile exec:java@calcite-codegen-test -Dexec.args="FilterMerge FilterJoin"   # some rules
```

This generates each rule into `Backends/Calcite/Generated/`, compiles it, and runs the matching test from `Backends/Calcite/Tests/`.
For `FilterMerge` it produces `Backends/Calcite/Generated/FilterMerge.java`.

CI runs the full check with `scripts/test-codegen.sh`, which also fails if a rule has no test or if `Generated/` is out of date, so regenerate
and commit it after changing a rule or the generator.

### CockroachDB

Rules are JSON `from`/`to` patterns applied by a Go package inside CockroachDB's optimizer. One-time setup clones a pinned CockroachDB commit
into `vendor/cockroach`, applies `cockroach.patch` and installs the runtime (needs `bazelisk`, and Xcode command line tools on macOS):

```sh
./scripts/setup-cockroach.sh
./scripts/test-cockroach.sh                     # generate, copy into CockroachDB, run the Go tests
./scripts/test-cockroach.sh -f TestMergeSelects # filter tests
./scripts/demo-cockroach.sh                     # build the server and show plans with rules OFF vs ON
./mvnw -q compile exec:java@cockroach-codegen   # only generate (optionally pass rule names via -Dexec.args)
```

To run rules in a CockroachDB server, point `COCKROACH_RULESCRIPT_DIR` at a directory of generated JSON files; see the
[CockroachDB README](src/main/java/org/qed/Backends/Cockroach/README.md).

### Apache Spark

Rules are JSON patterns interpreted by one generic Catalyst rule, `RuleScriptRule`. The backend is a separate Maven project
(`Backends/Spark/pom.xml`) so Spark's dependencies stay out of the main build.

```sh
./scripts/test-spark.sh                       # generate, then run the tests (JDK 21 recommended)
./mvnw -q compile exec:java@spark-codegen     # only generate
```

See the [Spark README](src/main/java/org/qed/Backends/Spark/README.md) for how
to install the rules into a session, and for the design discussion.

## QED Proofs on Rules

RuleScript turns each `RRule` into QED JSON and runs the Rust [QED prover](https://github.com/qed-solver/prover) against it to check
QED-level provability of the before/after pair.

You will need `jq`, `z3` and `cvc5` on your `PATH` (`scripts/install-dependencies.sh` installs them on Debian/Ubuntu) and a Rust nightly
toolchain. Read [qed-solver/prover](https://github.com/qed-solver/prover) for more details.

After you add or change rules, run the following from the `java/` directory:

```sh
./mvnw compile
bash scripts/generate-rule-json.sh    # QED JSON under tmp-rules/
bash scripts/build-qed-prover.sh      # clone ./qed-prover and build (skip if already built)
bash scripts/test-rules.sh            # run the prover on tmp-rules/*.json
```

After `scripts/test-rules.sh` finishes, the markdown summary is written to `tmp-rules/qed-prover-step-summary.md`.

To convert SQL (`.sql`) or `.cos` files to QED JSON, use the parser entry point:

```sh
./mvnw -q compile exec:java@qed-parser-main -Dexec.args="path/to/file.sql"
```

## Scripts

| Script | Purpose |
| --- | --- |
| `test-codegen.sh` | Calcite: hardcoding check, every rule has a test, generate + compile + test, `Generated/` is up to date. |
| `setup-cockroach.sh` | One-time: clone and patch CockroachDB into `vendor/cockroach`. |
| `test-cockroach.sh` | Generate rules, install into CockroachDB, run its Go tests. |
| `demo-cockroach.sh` | Build `cockroach-short` and compare `EXPLAIN (OPT)` with rules off and on. |
| `test-spark.sh` | Generate rules and run the Spark JUnit tests. |
| `generate-rule-json.sh` | Write QED JSON for every rule to `tmp-rules/`. |
| `build-qed-prover.sh` | Clone and build the QED prover. |
| `test-rules.sh` | Run the prover on `tmp-rules/*.json` and summarize. |
| `install-dependencies.sh` | Install `jq`, `z3`, `cvc5` and Rust (Debian/Ubuntu). |

## CI

Three GitHub workflows (in `../.github/workflows/`): `codegen-test.yml` (Calcite generation and tests), `prover-test.yml` (QED provability of
every rule) and `rust-test.yml` (the Rust crate).

## License

Copyright 2026 The Qed Team. Licensed under the Apache License, Version 2.0.
