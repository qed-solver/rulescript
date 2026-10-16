# Apache Spark

RuleScript rules run inside Spark's Catalyst optimizer as ordinary optimizer rules. Each rule is a JSON file (a `from` and a
`to` pattern); one generic interpreter, `RuleScriptRule`, matches the `from` pattern against a `LogicalPlan` and builds the
`to` pattern from what it matched.

## Layout

- `SparkGenerator.java` — translates a rule into JSON.
- `SparkTester.java` — generates all rules into `Generated/` and prints the ones it cannot express.
- `Generated/` — generated JSON rules, one per rule.
- `Runtime/` — the interpreter (plain Java against the Catalyst classes; no Scala toolchain needed).
- `Tests/` — JUnit tests; `pom.xml` — a separate Maven project, so Spark's dependencies stay out of the Calcite build.

## Run

From `java/`:

```sh
./scripts/test-spark.sh            # generate, then run the tests (JDK 21 recommended; 17 works, 25 works with a warning)
./mvnw -q compile exec:java@spark-codegen            # only generate
```

Use the rules in a session by putting the generated JSON in a directory and starting Spark with the runtime on the classpath:

```sh
RULESCRIPT_DIR=$PWD/my-rules spark-shell --conf spark.sql.extensions=org.qed.spark.RuleScriptExtensions
```

or programmatically: `RuleScriptExtensions.install(extensions, rules)` inside `SparkSession.builder().withExtensions(...)`.

## How Spark instantiates a rewrite rule

Catalyst has no declarative rule language. A rule is a Scala object extending `Rule[LogicalPlan]` with
`apply(plan): LogicalPlan`, usually `plan transform { case Filter(c, Filter(d, child)) => ... }`. The rules are listed by hand
in `Optimizer.defaultBatches` (`Batch(name, Once | FixedPoint, rules...)`) and `RuleExecutor` applies each batch until the plan stops
changing. This is where RuleScript rules can enter:

| Entry point | Where it runs | Notes |
| --- | --- | --- |
| `SparkSessionExtensions.injectOptimizerRule` (`spark.sql.extensions`) | operator-optimization batch, fixed point | the natural place for algebraic rewrites; chosen |
| `injectPreOperatorOptimizationRule` | `Once`, before the operator-optimization batch | Spark 5.0 only |
| `spark.experimental.extraOptimizations` | "User Provided Optimizers" batch, near the end | per session, no jar needed |
| `injectRuntimeOptimizerRule` | adaptive query execution | rules that need runtime statistics |
| `injectPostHocResolutionRule` / `injectResolutionRule` | analysis | sees `Distinct`, `Intersect`, `Except` before they are replaced |
| `spark.sql.optimizer.excludedRules` | — | switches Spark's own rules off, to see an injected rule at work |

Two Catalyst facts shape the translation. `Distinct`, `Intersect` and `Except` are replaced by aggregates and semi/anti joins in the early
"Replace Operators" batch, so a rule over them only fires if it is injected before that (analysis, or a direct call). And a rewrite must keep
the output attributes (`ExprId`s) of the node it replaces, because parents refer to them.

## Ways to bring a RuleScript rule to Spark, and the choice

1. **Generate Scala or Java source per rule** (the Calcite way). Native and readable, and a Spark committer could read it. Costs: every
   rule repeats the attribute, alias and output bookkeeping unless a runtime library carries it (and then it is half an interpreter);
   the generated code needs a compile step (scalac, or javac against the Spark classpath) before it can be tried; large patterns give
   large generated methods.
2. **Interpret a JSON pattern** (the CockroachDB way). One class holds all Catalyst knowledge; a rule is data, loaded by a config entry
   without a compile step, so a rule can be swapped per session; the tests exercise the real artifact. Cost: an interpreter to maintain
   (about 1,700 lines of Java here, plus a 630-line generator) and an IR to keep stable.
3. **A native pattern library written in Scala** (the DataFusion way). The same as 2 without serialization, but the rules would have to
   be written a second time in Scala, which defeats a single source of rules.
4. **Translate to a Spark-native declarative language** — there is none. This is the Spark counterpart of CockroachDB's optgen, and it
   would not help: Catalyst rules are code, and the equivalent of optgen's helper functions is the hand-written Scala around
   `transform`.
5. **Convert Spark plans to Calcite (for example through Substrait) and reuse the Calcite rules.** Reuses 385 generated rules, but the
   conversion is lossy (expression IDs, hints, nondeterminism) and adds heavy dependencies, so it proves less about Spark itself.

**Recommendation: option 2, with the JSON IR as the interface and `injectOptimizerRule` as the entry point.** It is the smallest thing that
runs every rule in a real optimizer, keeps rules as data, and puts the Spark-specific judgement (attributes, determinism, output
preservation) in one reviewed place. If a rule ever needs to be shipped inside Spark itself, option 1 can be added later as a printer over the same IR
(the IR already names every operator and symbol), without changing the tests.

## How the translation works

- A scan of the DSL binds any `LogicalPlan` with that many output columns; the same scan name twice requires `sameResult` plans. A
  scan the rule uses more often than it matched (the same relation appears twice in the result) is renewed with fresh column ids.
- An uninterpreted symbol `P(args)` is a small lambda: parameters (the plan attributes it reads) and a body (the Catalyst expression it
  matched). Using it in the `to` pattern substitutes the new arguments for the parameters. Symbols only match deterministic expressions
  (the DSL's functions are pure). A symbol applied to the value of another symbol, `P(D(x))`, to a literal, or to a concrete operator
  such as an `If`, is split into a context with a hole for that value.
- `joinField(i, other)` is the `i`-th column of the row of the join it is evaluated in (the left columns, then the right columns), the
  same reading the Calcite and CockroachDB backends have. Column references resolve against the node they are evaluated in, so a relation
  that occurs twice (a self join) is not confused with itself.
- The rewritten node keeps the output of the original: if the new plan has other attributes, a `Project` of `Alias(.., oldName)(oldExprId)` is added.
- Spark represents `a <> b` as `NOT (a = b)` and nested `AND`s as one conjunction, so some DSL rewrites are no-ops there; the tests count a match
  whose result equals the input as a pass.
- `UNION DISTINCT` is `Distinct(Union)` or, after the early batch, an `Aggregate` over all columns; both are matched, and the aggregate is built.
- Correlated constructs map to Catalyst's own nodes. `Exists` and `x IN (subquery)` are `Exists` and `InSubquery(ListQuery)` expressions,
  with `OuterReference` for the columns of the outer row. A DSL `Correlate` of kind inner or left is a `LateralJoin` over a `LateralSubquery`
  whose plan is the correlation filter; kinds semi and anti are the `LeftSemi` and `LeftAnti` join with that condition (what the
  optimizer turns a correlated `EXISTS` into). The correlated conjuncts that Catalyst moves into `Exists.joinCond`
  (`PullupCorrelatedPredicates`) are read as a filter inside the subquery.
- Relations and expressions that a rule defines for itself (`IntScan`, `RawFilter`, `AnyFilter`, `NotAnyJoin`, `Exists` with guards, ...) are
  translated from what their `semantics()` returns, with their relation components kept as relations, so no rule needs a special case.

## What the tests show

`GenericRulesTest` builds a concrete plan (random integer relations, deterministic expressions for the symbols) from each rule's own
`from` pattern, applies the rule, and checks that it fires, keeps the output ids, and returns the same rows as the original plan on random
data (6 instances per rule). `EndToEndTest` injects rules through `SparkSessionExtensions`, runs SQL, and checks that the rule fired in the real
optimizer and that results are unchanged. `PLANS=1` prints the plan before and after for the first instance, `RULE=A,B` runs only those rules.

Status: all 385 rules translate and pass the generic soundness test, with no rule set aside; four end-to-end tests pass.

Two things the first version got wrong, and what they showed:

- `PruneJoinRightCols` and `PruneSemiAntiJoinRightCols` looked unsound. The rules were right: they call `joinField` on the right relation, and
  the translation had read the relation arguments as the order of the columns instead of the position in the join row.
- `SimplifyLeftJoinWithZeroRowsRight` and `TryDecorrelateWindow` were not matched. Both were gaps of the interpreter (the columns of an empty relation,
  and a relation used twice that is not a plain relation), now handled.

Where a rule enters matters. The optimizer's early batches rewrite an uncorrelated `EXISTS` into a scalar subquery and move correlated predicates into
`Exists.joinCond` before the operator-optimization batches that `injectOptimizerRule` joins, and `RewritePredicateSubquery` and `RewriteLateralSubquery` cannot be
excluded. Rules over the shapes that exist before that run through `RuleScriptExtensions.installAfterAnalysis`.

Limits worth knowing: a scan matches exactly the arity written in the rule (like the Calcite backend); symbols applied to symbols more than
one level deep are reported as unsupported; the runtime is written against Spark 4.1.3 (`pom.xml`) and will need small signature updates for
other versions.
