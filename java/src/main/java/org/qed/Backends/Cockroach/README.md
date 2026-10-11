# CockroachDB

Each rule is written as a JSON file (`from` and `to` patterns), which a Go package inside CockroachDB applies to
optimizer expressions.

## Layout

- `Rules/` — the RuleScript rules.
- `CockroachGenerator.java` — translates a rule into a JSON file.
- `Generated/` — generated JSON rules, one per rule.
- `Runtime/` — Go package that applies a generated rule; copied into CockroachDB as `pkg/sql/opt/rulescript`.
- `Tests/` — Go tests, one per rule, plus shared helpers.
- `cockroach.patch` — the change to CockroachDB that lets the runtime run.
- `CockroachTester.java` — generates all rules into `Generated/`.

## Generate Rules and Execute Tests

From `java/`, once (clones CockroachDB into `java/vendor/cockroach`; needs `bazelisk`):

```sh
./scripts/setup-cockroach.sh
```

Then:

```sh
./scripts/test-cockroach.sh
./scripts/test-cockroach.sh -f TestMergeSelects
./mvnw -q compile exec:java@cockroach-codegen -Dexec.args="MergeSelects MergeSelectInnerJoin"
```

The first generates, copies into CockroachDB and runs the tests (`-f` filters them); the last only generates the listed rules.

## Use in a CockroachDB server

Build CockroachDB after `setup-cockroach.sh` (`cd vendor/cockroach && ./dev build cockroach-short`), then start it with the
generated rules loaded:

```sh
COCKROACH_RULESCRIPT_DIR=<path to Generated/> ./cockroach-short demo
```

Every query is then optimized with the rules (they run as `ExternalNormalizeRule`, which can be switched off with
`SET disable_optimizer_rules`). Rules whose output contains their own pattern (so they would keep growing the plan) are skipped at startup with a message. `./scripts/demo-cockroach.sh` builds the server and prints plans with the rules off and on.
