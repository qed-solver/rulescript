# RuleScript — Java DSL

RuleScript is an engine-agnostic domain-specific language for developing query rewrite rules.
For details, please see our [paper](https://arxiv.org/abs/2605.05536).

For the Rust/DataFusion backend, see the [root README](../README.md).

## Build

The project targets Java 25. Build with Maven:

```sh
./mvnw compile -q
```

## Generate Rules

Rules are defined in `src/main/java/org/qed/RRuleInstances/` as Java records implementing `RRule`. Each rule provides a `before()` pattern and an `after()` transformation in terms of RuleScript's relational algebra operators. The generators pick up every file in that directory automatically.

Example: `FilterMerge`

```java
// src/main/java/org/qed/RRuleInstances/FilterMerge.java
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

RuleScript supports two styles of backend integration:

**Code-generation backends** translate `RRule` definitions into engine-specific artifacts. Run the corresponding generator to produce files under each backend's `Generated/` directory:

```sh
./mvnw -q compile exec:java@cockroach-codegen    # CockroachDB
./mvnw -q compile exec:java@calcite-codegen-test # Apache Calcite
./mvnw -q compile exec:java@mysql-tester         # MySQL
```

For `FilterMerge` this produces:
- `src/main/java/org/qed/Backends/Calcite/Generated/FilterMerge.java` — Apache Calcite rule
- `src/main/java/org/qed/Backends/Cockroach/Generated/FilterMerge.opt` — CockroachDB optgen rule

**Native runtime backend** — rules are defined and applied directly through backend integration. See the [Datafusion backend](../README.md) for details.

## Qed Proofs on Rules

RuleScript turns each `RRule` into QED JSON and runs the Rust [Qed prover](https://github.com/qed-solver/prover) against it to check QED-level provability of the before/after pair.

You will need to install `jq`, `z3`, and `cvc5` yourself and put them on `PATH`. Read [qed-solver/prover](https://github.com/qed-solver/prover) for more details.

After you add or change rules, run the following from the `java/` directory:

```sh
./mvnw compile
bash scripts/generate-rule-json.sh    # QED JSON under tmp-rules/
bash scripts/build-qed-prover.sh      # clone ./qed-prover and build (skip if already built)
bash scripts/test-rules.sh            # run the prover on tmp-rules/*.json
```

After `scripts/test-rules.sh` finishes, the markdown summary is written to `tmp-rules/qed-prover-step-summary.md`.

## License

Copyright 2026 The Qed Team. Licensed under the Apache License, Version 2.0.
