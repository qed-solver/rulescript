# RuleScript

RuleScript is an engine-agnostic domain-specific language for developing query rewrite rules.
For details, please see our [paper](http://www2.eecs.berkeley.edu/Pubs/TechRpts/2024/EECS-2024-140.pdf).

## Build

The project targets Java 25. Build with Maven:

```sh
./mvnw compile -q
```

## Generate Rules

Rules are generated per backend by running the corresponding tester:

```sh
./mvnw -q compile exec:java@cockroach-codegen    # CockroachDB
./mvnw -q compile exec:java@calcite-codegen-test # Apache Calcite
./mvnw -q compile exec:java@mysql-tester         # MySQL
# See the Datafusion folder for details about RuleScript generation for DataFusion
```

Generated rule files are written to each backend's `Generated/` directory.

## Adding Rules

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
        return source.filter(inner).filter(outer);   // source.filter(P).filter(Q)
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.and(inner, outer)); // source.filter(P AND Q)
    }
}
```

Running the generators will produce:
- `src/main/java/org/qed/Backends/Calcite/Generated/FilterMerge.java` — the Apache Calcite rule implementation
- `src/main/java/org/qed/Backends/Cockroach/Generated/FilterMerge.opt` — the CockroachDB optgen rule

For a full description of the rule language and available operators, see the [paper](http://www2.eecs.berkeley.edu/Pubs/TechRpts/2024/EECS-2024-140.pdf).

## Qed Proofs on Rules

RuleScript turns each `RRule` into Qed JSON and runs the Rust [Qed prover](https://github.com/qed-solver/prover) against it to check Ged-level provability of the before/after pair.

You will need to install `jq`, `z3`, and `cvc5` yourself and put them on `PATH`. Read [qed-solver/prover](https://github.com/qed-solver/prover) for how to install compatible versions.

After you add or change rules as Java records in `src/main/java/org/qed/RRuleInstances/`, run the following from the repository root:

```sh
./mvnw compile
bash scripts/generate-rule-json.sh    # Qed JSON under tmp-rules/
bash scripts/build-qed-prover.sh      # clone ./qed-prover and build target/release/qed-prover (skip if already built)
bash scripts/test-rules.sh            # run the prover on tmp-rules/*.json
```

## License

Copyright 2026 The Qed Team

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with
the License. You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "
AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific
language governing permissions and limitations under the License.
