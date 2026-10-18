# Apache Calcite

## Layout

- `../../RRuleInstances/` — the RuleScript rules.
- `CalciteGenerator.java` — translates a rule into a Calcite `RelRule`.
- `CalciteRuntime.java` — helpers called by the generated rules.
- `Generated/` — generated Calcite rules, one per rule.
- `Tests/` — one test per rule, plus tests for the generator.
- `CalciteTester.java` — generates the rules, compiles them and runs the tests.
- `EmptyConfig.java` — base config for the generated rules.

## Generate Rules and Execute Tests

From `java/`:

```sh
./mvnw -q compile exec:java@calcite-codegen-test
```

To run only some rules:

```sh
./mvnw -q compile exec:java@calcite-codegen-test -Dexec.args="FilterMerge FilterJoin"
```
