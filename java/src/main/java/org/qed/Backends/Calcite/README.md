# Apache Calcite

## Layout

- `../../RRuleInstances/` — RuleScript rules (`RRule` records, one per file) that are turned into Calcite rules.
- `CalciteGenerator.java` — translates an `RRule` into a Calcite `RelRule` class.
- `CalciteRuntime.java` — small helpers called by generated rules (column references by absolute ordinal,
  substitution of symbol arguments, scan constraints, correlates, constant relations).
- `Generated/` — generator output, one `RelRule` per rule. Never edit these files; regenerate them.
- `Tests/` — one `<Rule>Test.java` per rule, plus `CalciteGeneratorTest` for the generator itself, and the shared
  `RuleChecks`, `FaithfulPlans` and `Mutants`.
- `CalciteTester.java` — generates the rules, compiles the output, and runs the tests on the freshly compiled classes.
- `EmptyConfig.java` — base `RelRule.Config` that generated rules extend.

## Generate Rules and Execute Tests

From `java/`:

```sh
./mvnw -q compile exec:java@calcite-codegen-test
```

This (1) writes `Generated/*.java` for every rule, (2) compiles each generated file in-process, (3) runs `runTest()`
of every class in `Tests/` named `*Test` against the compiled output, and (4) exits non-zero if a rule could not be
generated or compiled or if any check failed. A rule that cannot be generated has its stale output removed.

To work on some rules only, pass their names (their tests are the only ones run):

```sh
./mvnw -q compile exec:java@calcite-codegen-test -Dexec.args="FilterMerge FilterJoin"
```

`scripts/test-codegen.sh` is the CI entry point: it also rejects special-casing of rules in the generator
(`scripts/check-no-hardcoding.sh`), requires a test for every rule, and checks that the committed `Generated/` is what
the generator writes.

## How a rule is translated

The generator works from the structure of `before()` and `after()` only (see `CodeGenerator` for the traversal
hooks); it never looks at a rule's name. A construct it cannot translate raises
`CodeGenerator.UnsupportedConstructException` and no file is written.

**`before()` becomes the matcher.** The operand tree mirrors the pattern (`LogicalFilter`, `LogicalJoin`, ...; a
scan matches any relation). `onMatch` starts with checks for everything else the pattern states: join type, the
`ALL` flag and arity of set operations, field counts, uniqueness (`Scan.unique`, via metadata), non-nullable
columns, columns that share a type variable having equal types, a scan that occurs twice being the same relation,
boolean literals, column references, `AND`/`OR`/`NOT` structure (nested connectives of one kind are flattened, as
Calcite flattens conditions), concrete operators such as `=` and `IS NULL`, constant `VALUES`, empty relations.

**Symbols are metavariables.** An uninterpreted predicate or projection `P(a1..an)` is bound to what it matched:
- if all arguments are columns (or symbols already bound), `P` is bound to the whole matched expression, which must
  read nothing but its arguments (`CalciteRuntime.abstracts`);
- if an argument is a compound pattern, `P` is bound to the operator of the matched call and the arguments are matched
  against its operands.

Aggregate functions are bound to the matched `AggregateCall`. Group keys and aggregate arguments that are not plain
columns are resolved through the projection that `RelBuilder.aggregate` puts below the aggregate.

**`after()` becomes the construction.** `RelBuilder` calls rebuild the pattern; every symbol is instantiated for the
arguments it is applied to now (`CalciteRuntime.instantiate` substitutes new arguments for the old ones, so
`F(project.field(0))` becomes `F(<project expression>)`). The builder is configured by `CalciteRuntime.builder`: it
does not simplify expressions and does not merge adjacent projects, because that would change or undo the structure
that the rule states (for example, the builder folds the expansion of `IS NOT DISTINCT FROM` back into the original
call). It still drops identity projections and `TRUE` filters.

## Writing Calcite Tests

A test is a class in `Tests/` with a public static `runTest()`. `RuleChecks` provides the checks; the rule under test
is looked up by name from the generated classes (`RuleChecks.generated`), so a test cannot pass on a substitute.

Every rule test has two parts.

**Checks derived from the pattern** (the same three lines in every test). They are broad but share their reading of
the rule with the generator, so they cannot be the only evidence:
- `rewritesPattern(rule)` — builds the plan of `before()` operator for operator (`FaithfulPlans`; `semantics()`
  would simplify it and often remove the pattern), applies the generated rule, and compares with `after()`. The rule
  must fire (the planner's rule-production event is checked, so a rewrite that the builder absorbs still counts).
- `rewritesConcretePredicates(rule)` — the same with real predicates (`=`, `<`, `IS NULL`, ...) instead of
  uninterpreted symbols.
- `rejectsNearMisses(rule)` — the rule must not fire on copies of `before()` with one condition broken (`Mutants`):
  another join type, a non-unique or nullable scan, `ALL` vs `DISTINCT`, a flipped literal, `AND` vs `OR`, or a
  missing operator.

**Hand-written cases** (`ordinaryTables` in each test). Real tables, real expressions (`+`, `*`, `>`, `=`, `SUM`, ...)
and an input plan and an *expected output plan written out by hand* from what the rule means (for example, for
`ProjectMerge` the expected plan is `Project(Top(Bottom(x)))` over the scan). Each also has plans that look similar
but must not be rewritten: the wrong join type, a key that is not unique, a conjunct that reads the wrong column, an
aggregate that is not a plain DISTINCT. These do not depend on how the generator reads the pattern, so a shared
misreading would fail them. `RuleChecks.rewrites(rule, before, after)` and `RuleChecks.doesNotApply(rule, plan)` are
the assertions.

Plans are built with `RuleChecks.ruleBuilder`, the builder configuration of generated rules (no expression
simplification, no project merging, no pruning of aggregate inputs). It still drops identity projections and `TRUE`
filters, so a few expected plans are shorter than the rule's `after()` suggests; the tests say so where it happens.

`CalciteGeneratorTest` tests the generator itself: independence from rule names, determinism, that `Generated/` is
current, that untranslatable patterns are refused, and (with a rule that matches a correlate) that operands are
numbered in Calcite's pre-order.

## Limitations

- Calc-like rules are modeled in RuleScript as `Filter` + `Project` and are generated that way (there is no
  `LogicalCalc` in the pattern vocabulary). Because the builder drops an identity projection and a `TRUE` filter,
  `FilterToCalc` and `ProjectToCalc` rewrite a plan into itself; their tests check that the rule fires.
- The generated rules implement the restricted rules of RuleScript (only rules tagged `// SCOPE: FULL` are complete; the rest), which are
  narrower than the Calcite rules they come from (fixed arity, one join type, `UNION ALL` only, ...).
- A conjunction in a pattern matches the conjuncts of the condition in order, as the pattern lists them. A condition
  whose conjuncts are in another order is not matched.
- Rules that rewrite a plan into an equivalent plan of the same shape (`JoinCommute`, `JoinAddRedundantSemiJoin`,
  `IntersectReorder`, `JoinDeriveIsNotNullFilter`, `JoinToHyperGraph`, ...) match their own output, so a planner
  must bound them (the tests use a match limit of one); Calcite's versions guard against this.
- A symbol bound to a conjunct that is itself a conjunction is flattened by Calcite into several conjuncts and no
  longer matches a pattern that counts conjuncts.
- Rules are generated for `Logical*` operators. Under Volcano an input operand matches a `RelSubset`; only
  `HepPlanner` is tested.
- Constant relations support boolean, integer and string literals.
