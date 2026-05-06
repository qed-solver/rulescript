# CockroachDB

This directory contains CockroachDB-specific artifacts generated from RuleScript rules.

## Generate Rules

Run the Cockroach generator from the repository root:

```sh
./mvnw -q compile exec:java@cockroach-codegen
```

Generated optgen rules are written to:

- `src/main/java/org/qed/Backends/Cockroach/Generated/*.opt`

Cockroach test cases are maintained in:

- `src/main/java/org/qed/Backends/Cockroach/CockroachTests`

## Running against CockroachDB

1. Clone [cockroachdb/cockroach](https://github.com/cockroachdb/cockroach) and check out commit:

   ```text
   4b80cd59c6299f26b2b4f02a96064d5127ccad94
   ```

2. Copy RuleScript outputs into the Cockroach tree:

   - Rule files from `Generated/*.opt` -> `pkg/sql/opt/norm/rules/`
   - `CockroachTests` -> `pkg/sql/opt/norm/testdata/rules/CockroachTests`
   - Replace `pkg/sql/opt/norm/reject_null_funcs.go` in the CockroachDB repository with the current copy from this repo (`src/main/java/org/qed/Backends/Cockroach/reject_null_funcs.go`), because the original code near line 257 performs an unchecked type assertion `agg.Child(0).(*memo.VariableExpr)`; when `AggregateProjectMerge` fires and merges directly, that child can be a `PlusExpr`, which causes a panic, and the patched version adds a safe type check.

3. In the Cockroach repository:

   ```sh
   ./dev doctor
   ./dev build
   ./dev test pkg/sql/opt/norm -f=TestNormRules/CockroachTests -v
   ```

This validates that generated rules compile and behave as expected in Cockroach's optimizer test harness.
