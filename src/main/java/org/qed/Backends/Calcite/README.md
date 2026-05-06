# Apache Calcite

This directory contains Apache Calcite-specific artifacts generated from RuleScript rules.

## Generate Rules and Execute Tests

Run the Calcite generator from the repository root:

```sh
./mvnw -q compile exec:java@calcite-codegen-test
```

This command does two things:

1. Generates Calcite rule classes into:
   - `src/main/java/org/qed/Backends/Calcite/Generated/*.java`
2. Runs all Calcite backend tests discovered under:
   - `src/main/java/org/qed/Backends/Calcite/Tests/*Test.java`

## Writing Calcite Tests

Calcite tests are Java classes in `src/main/java/org/qed/Backends/Calcite/Tests/` with a public static `runTest()` method.

Typical flow in each test:

1. Build `before` and `after` plans with `RuleBuilder`.
2. Load the generated rule into a `HepPlanner` through `CalciteTester`.
3. Call `tester.verify(runner, before, after)`.

`CalciteTester.runAllTests()` reflects over all `*Test.java` files and invokes `runTest()` automatically during `calcite-codegen-test`.
