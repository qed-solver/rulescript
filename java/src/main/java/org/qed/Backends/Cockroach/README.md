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

Build CockroachDB after `setup-cockroach.sh` (`cd vendor/cockroach && ./dev build cockroach-short`). The server loads every
`*.json` rule file from the directory in `COCKROACH_RULESCRIPT_DIR`; any rule you want in the optimizer is just a file there.

1. Generate the rules (`./mvnw -q compile exec:java@cockroach-codegen`); they are written to `Generated/`.
2. Make a directory holding only the rules you want, for example a few by name or every rule of one origin:

   ```sh
   mkdir my-rules
   cp Generated/MergeSelects.json Generated/UnionToDistinct.json my-rules/
   for f in ../../RRuleInstances/*.java; do grep -q '^// ORIGIN: Calcite' "$f" && cp Generated/$(basename "$f" .java).json my-rules/; done
   ```

3. Start CockroachDB with that directory:

   ```sh
   COCKROACH_RULESCRIPT_DIR=$PWD/my-rules ./cockroach-short demo
   ```

   On startup it prints `rulescript: loaded N rules from ...` and one `rulescript: skipped ...` line per file it did not load.
   A rule is skipped when its output contains its own pattern (it would keep growing the plan).
4. Every query is now optimized with the rules, as `ExternalNormalizeRule`. Turn them off for a session with
   `SET disable_optimizer_rules = 'ExternalNormalizeRule'`. Without `COCKROACH_RULESCRIPT_DIR` the server is unchanged.

To see which rules fire, also set `COCKROACH_RULESCRIPT_LOG=<file>`; each time a rule rewrites an expression its name is
appended to that file (`sort <file> | uniq -c` gives the counts).
