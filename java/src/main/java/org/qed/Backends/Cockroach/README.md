# CockroachDB

Rules are not compiled to CockroachDB's optgen language. `CockroachGenerator` writes each rule as a small JSON file, and a
Go package inside CockroachDB (`Runtime/`) applies that file directly to optimizer expressions.

## Layout

- `CockroachGenerator.java` — translates an `RRule` into a JSON file.
- `Rules/` — the rules (`RRule` records, one per file): the CockroachDB normalization rules, ported to RuleScript and proved by QED.
- `Generated/` — generator output, one `<Rule>.json` per rule.
- `Runtime/` — the Go package that reads a generated rule, matches its `from` pattern against a CockroachDB expression
  and builds its `to` pattern. It is copied into CockroachDB as `pkg/sql/opt/rulescript`.
- `Tests/` — Go tests, one `<rule>_test.go` per rule, plus `helpers_test.go` (environment and plan helpers), `shared_test.go` (test bodies and SQL checks reused by several rules) and `native_rules_test.go` (comparison with CockroachDB's own rules).
- `cockroach.patch` — the change to CockroachDB that lets the runtime run (a hook in `norm.Factory` and a rule name).
- `CockroachTester.java` — generates every rule into `Generated/`.

## Generate Rules and Execute Tests

From `java/`, once (clones CockroachDB into `java/vendor/cockroach`; slow, needs `bazelisk` and the Xcode command line
tools on macOS):

```sh
./scripts/setup-cockroach.sh
```

Then:

```sh
./scripts/test-cockroach.sh                  # check, generate, copy into CockroachDB, run the tests
./scripts/test-cockroach.sh -f TestMergeSelects
./mvnw -q compile exec:java@cockroach-codegen -Dexec.args="MergeSelects MergeSelectInnerJoin"   # generate only some rules
```

Edit `Runtime/` and `Tests/` here, not the copy inside `vendor/`, which is overwritten on every run.

## Generated rule format

```json
{ "name": "MergeSelects", "mode": "normalize", "from": <relation>, "to": <relation> }
```

`from` is the pattern to find and `to` the plan to build in its place. `mode` is `normalize` (replace the matched
expression; the only mode implemented) or `explore` (add the rewrite as an alternative).

Relations carry an `id`, which names that pattern node (identical sub-patterns get the same id):

| `op` | fields | meaning |
|---|---|---|
| `scan` | `name`, optional `arity`, `unique`, `non_null` | a relation symbol: matches any relation (with `arity` columns, if given) and is bound to its name. `unique` requires the output column set to be a strict key; `non_null` lists zero-based output positions that must be non-null |
| `filter` | `cond`, `input` | `Select` |
| `project` | `exprs`, `input` | `Project`; a `proj`, concrete scalar, or `field_alias` entry computes a column and a `field` entry passes through that input column. `exprs` gives the RuleScript output order |
| `join` | `type` (`inner`, `left`, `right`, `full`, `semi`, `anti`), optional `apply`, `cond`, `left`, `right` | the corresponding CockroachDB join; `apply: true` selects an Apply join for `inner` or `semi` |
| `set` | `type` (`union`, `intersect`, `except`), `all`, `left`, `right` | a two-input set operation; `all` selects the bag variant |
| `empty` | `arity` | zero-row `Values` relation with exactly that many output columns |
| `values` | `rows` (arrays of `int`, `true`, or `false` scalar objects); optionally `reuse_cols_of` (matched relation id) and `ordinals` | concrete `Values` with the listed rows; all rows have the same width. A new root Values uses the matched root output IDs. A new nested Values reuses the listed matched input columns when each target column has a unique matching literal sequence |
| `aggregate` | `groups`, `aggs`, `input` | `GroupBy` with grouping keys and aggregation items; an empty `groups` list denotes `ScalarGroupBy` |

Scalars:

| `op` | fields | meaning |
|---|---|---|
| `pred` | `name`, `args` | an uninterpreted predicate applied to `args` |
| `proj` | `name`, `args` | an uninterpreted scalar function applied to `args` |
| `and`, `and_scalar`, `or`, `true`, `false`, `not` | `args` or `arg` for `not` | filter-list conjunction, exact binary scalar And, scalar disjunction, empty condition, false filter item, and scalar negation |
| `row` | `rels` (ids) | as an argument: all columns of those relations, in order |
| `field` | `ordinal`, `of` (id) | as an argument: one column of that relation |
| `field_alias` | `ordinal`, `of` (id) | a computed Project item that copies the referenced input column to a distinct output column ID; emitted for a repeated RuleScript Project field |
| `joinfield` | `ordinal`, `left`, `right` (ids) | legacy inline form for a column of concatenated relations; generated rules use `field` with the id of the expression's actual input relation |
| `group_key` | `name`, `args` | an uninterpreted scalar key computed below `GroupBy`; it may also be an aggregate argument |
| `agg` | `name`, `args`, `distinct` | an uninterpreted aggregate call bound to a CockroachDB aggregation item |
| `scalar` | `operator`, `args` | one concrete scalar; `=`, `<>`, `<`, `<=`, `>`, `>=`, and `IS NOT DISTINCT FROM` take two operands; `IS NULL`, `IS NOT NULL`, `IS NOT FALSE`, and unary `-` take one. A concrete scalar, `true`, `false`, or `null` can also occupy a computed `project` entry |
| `in_single`, `not_in_single` | `args` (left and right) | `In` or `NotIn` with a one-element tuple on the right |
| `null` | `type` (`bool`, `int`, or `same_as`); `same_as` also has `refs` (`of` relation id and `ordinal`) | a typed SQL NULL literal; matches a CockroachDB `Null` scalar or a cast of `Null` to that type, and builds a NULL of the declared type. `same_as` takes its type from the matched column references and requires all referenced columns to have equivalent types |
| `int` | `value` | a concrete integer scalar literal; matches and builds a CockroachDB integer constant |
| `exists` | `input` (relation) | a scalar `Exists` subquery. The matcher checks the complete relational body; rebuilding an `Exists` reuses the matched `ExistsPrivate`, including its lazy evaluation column and subquery metadata. A rule may match and rebuild at most one `Exists` expression |

A NULL operand of `and`, `and_scalar`, `or`, or `not` is typed as `bool` in JSON because these operators require
boolean arguments. A top-level NULL filter must also be boolean. A RuleScript type variable is encoded as
`same_as` when the before-pattern contains columns of that type; its `refs` retain every occurrence of the type
variable. Other NULL types fail generation with their type named in the diagnostic.

The generator may combine a predicate's complete sequence of input columns into one `row` argument, allowing
an uninterpreted filter list to range over a relation of any arity. Projection symbols keep their individual
`field` arguments because a computed scalar's argument count is fixed. A RuleScript `JoinField` becomes a
`field` of the expression's actual input relation. When a one-column RuleScript `Scan` supplies that positional
join reference, its JSON `arity` is set to 1 so the ordinal keeps the same meaning in CockroachDB.

The unary null checks match CockroachDB's `Is` or `IsNot` scalar with a NULL right operand; `IS NOT FALSE`
matches `IsNot` with a FALSE right operand. The current format treats `pred` and `proj` as uninterpreted symbols. The generator emits them only for
`RuleBuilder.QedFunction` operators. A `scalar` filter matches exactly one CockroachDB filter item and checks
its operator and operand tree. Its operands currently support `field`, `joinfield`, `true`, `false`, and a bound
uninterpreted `proj` or `pred` scalar. The latter symbols bind one scalar expression and its argument columns for
substitution. `not` matches one scalar `Not` expression in a filter item. A top-level `pred` still binds an entire
filter list. `or` with two or more arguments matches one scalar `Or` tree whose ordered leaves equal those
arguments, regardless of binary association. Rebuilding more than two arguments groups them from the left.
A `pred` whose arguments contain bound `proj` symbols matches one scalar filter item with exactly that many
direct scalar operands. Each `proj` binds its operand expression and the complete set of columns it reads.
The predicate's scalar root is retained; rebuilding replaces the matched operand subtrees with the new arguments.
When such a `proj` becomes a computed Project item, its output column gets a fresh ID of the operand's type.
A one-argument RuleScript `or` whose only child is equality of two scalar operands is encoded as `in_single`;
`not` of that form is encoded as `not_in_single`. These are the definitionally equivalent CockroachDB shapes for
single-element `IN` and `NOT IN`. Other one-argument `or` forms fail generation because CockroachDB has no unary
Or node and erasing it could turn a rewrite into an identity match.
`and` combines filter items in `to`. In `from`, a flat `and` matches either one CockroachDB filter item per child
or one filter item containing a scalar `And` tree, with leaves in the written order. A `true` child only matches
inside the scalar `And` tree, never as an independent filter item. Rebuilding `and` creates one filter item per
non-`true` child. A nested RuleScript `and` is encoded as `and_scalar` throughout that scalar tree, preserving its
binary grouping. An `and` below `not` or `or` is also encoded as `and_scalar`. `and_scalar` matches one scalar
And node in one filter item; its output remains one scalar And node.
For `project`, each `field` is passed through using CockroachDB's passthrough column set; each `proj` is a
CockroachDB projection item. The interpreter matches the complete set of passthrough columns and all projection
items. Concrete computed projection entries match their scalar tree exactly. A rebuilt concrete entry retains the
matched Project's output column ID at that position; a Project on the output path of the replacement retains its
matched root output IDs when its arity agrees. Repeating a `field` in a Project makes each additional occurrence
a `field_alias`, since CockroachDB output columns are a set of column IDs. The alias uses the matched Project's
output ID, or the matched root ID when rebuilding a root Project. A rule with multiple joins matches only when every
matched join has a default `JoinPrivate`; its replacement joins also receive default private data. This supports
join rearrangement without assigning one matched join's private options to a different join. A constrained
one-column RuleScript scan has `arity: 1` so its key and null constraints apply to its complete row, not an
arbitrarily wider CockroachDB relation. A scan without a constraint remains a relation wildcard.

A semi or anti join's `cond` sees the concatenated left and right input columns, while its relation output and any
parent expression see only the left columns. The other join types output both sides' columns. A matched join's
`JoinPrivate` is copied into its rebuilt join.
An Apply join emitted from RuleScript `Correlate` places the correlation condition in the join's `cond`. It
matches only when the right input has no outer-column reference to the left input, as required by the
before-pattern's independent right relation. The runtime preserves the matched `JoinPrivate`.
Repeated `scan` symbols under a join denote structurally identical relation instances. A match requires separate
output column IDs, the same catalog table, the same scanned column ordinals and types in output order, and the same
remaining scan options. The matcher records each occurrence's columns separately in left-to-right traversal order;
`field` references to a join use those distinct columns. Rebuilding consumes matching instances in that order.
When the replacement retains only one instance, it selects the occurrence whose output IDs match the root output
when possible. A replacement with incompatible output IDs is rejected; a type-preserving output Project may rename
the surviving columns to the matched root IDs. A repeated symbol over any other relation operator matches only
when the optimizer expressions are identical and their output IDs do not overlap at the join.
For a matched set operation, the ordered `SetPrivate.LeftCols`, `RightCols`, and `OutCols` establish the input-to-output
column mapping. The interpreter requires them to agree with the JSON input order and a matched set operation with
an empty ordering. Rebuilt set operations select the matched output column IDs using their corresponding left and
right input columns. When a rewrite removes the set operator, a Project renames the surviving columns to the
matched output IDs. An input mapping that cannot be established aborts the rewrite.
A newly built set operation can replace the root when both inputs have the same column count and that count equals
the matched root output arity. Its input column mappings follow JSON order, its output uses the matched root IDs,
and it has empty required ordering.
When a newly built set operation reuses columns on both inputs, the interpreter inserts a Project over its right
input with fresh column IDs of the same types. This satisfies CockroachDB's disjoint-input requirement while the
set operation still exposes the matched root output IDs.
An `empty` pattern binds a zero-row `Values` expression and its ordered `ValuesPrivate.Cols`. Rebuilding the
same `empty` node reuses that expression. A new root `empty` uses the matched root output column IDs. A new
nested `empty` fails generation because the format has no way to choose its output column IDs.
Concrete `values` checks its entire row matrix and literal column types. A rebuilt node with the same id reuses
the matched Values; a new root node uses the matched root output IDs. For a new nested Values, the generator
records a unique ordinal mapping from a matched Values row matrix to its own columns. It fails if that mapping
cannot be established.
An `aggregate` maps to `GroupBy`. Direct `field` keys become `GroupingPrivate.GroupingCols`. For a computed
`group_key`, the interpreter requires a Project immediately below `GroupBy`: its passthrough columns are a subset
of the source output containing all directly referenced columns, and its computed items correspond to the computed keys in JSON order. Building an aggregate inserts
the same Project, retaining the bound grouping-key output column IDs. `aggs` match aggregation items in order;
their scalar expressions are bound as uninterpreted aggregate symbols, including the presence or absence of
`AggDistinct`, and are rebuilt by replacing their argument columns. If `distinct` changes in `to`, the interpreter adds or removes the `AggDistinct` wrapper while retaining the aggregate function and any `AggFilter` wrapper. An aggregate without computed keys has no
inserted Project. Only canonical `GroupBy` with empty required ordering, ordinary null grouping, and no error-on-
duplicate behavior matches.
With no grouping keys, `aggregate` matches and builds `ScalarGroupBy` with an empty grouping column set. Its
aggregation items follow the same binding rules as `GroupBy`.
An aggregate argument that is a `group_key` binds an additional computed item in that same input Project, after
the computed grouping keys in JSON order. It must be a direct argument column of the aggregate call. The input
Project has exactly those computed items and may pass through any subset of source columns as long as every
direct `field` or `row` reference in the aggregate remains available. A later grouping key with the same
symbol reuses its bound item and output column ID.

A symbol in `from` is bound to the expression it matched, together with the columns it was matched over, and the match
only succeeds if the expression reads no other column. In `to` the symbol is rebuilt for the arguments it is applied to
now: if they are the same columns the original expression is reused, otherwise each original column is replaced by the new
argument. Repeating a predicate symbol in `from` is supported when its JSON arguments and filter/scalar context are
identical at every occurrence. Each occurrence must match the same bound expression and argument columns. Other
repeated symbols fail generation. A rewrite is dropped unless its output columns equal those of the expression
it replaces. The columns of a `scan` are its output columns in column-id order, of a `project` its projected columns, of a
`join` the left then the right columns.
An installed rule is not reapplied while its own replacement is being constructed. This prevents a structural
permutation from recursively rebuilding itself inside the factory.

### Differences from native rule firing

The generated rewrite follows its proved RuleScript `before()` and `after()` patterns. Some ports intentionally
have fewer firing guards or a narrower operator shape than their CockroachDB namesakes. The native comparison tests
record these differences:

- `InlineProjectInProject` omits native `HasDuplicateRefs`.
- `TryDecorrelateSelect` and `TryDecorrelateProject` omit native outer-column guards.
- `TryDecorrelateSemiJoin` likewise omits the native outer-column guard. Its native comparison uses a
  hand-built `SemiJoinApply` because SQL planning represents the query as scalar `Exists` before this rule fires.
- `TryRemapJoinOuterColsLeft` and `TryRemapJoinOuterColsRight` follow their explicit RuleScript
  correlated-join patterns; native rules use additional outer-column analysis, so their SQL comparison plans differ.
- `HoistUnboundFilterFromExistsSubquery` and `HoistUnboundJoinFilterFromExistsSubquery` hoist a
  zero-argument predicate from a scalar `And` inside `Exists`; the native rules leave that SQL plan in place
  because their extra hoistability guard and filter-item shape do not match it.
- `HoistSelectExists`, `HoistSelectNotExists`, and `HoistJoinSubquery` match a scalar `And` tree
  containing `Exists`; the native rules require separate filter items in these SQL comparisons.
- `EliminateExistsZeroRows` has a hand-built native comparison because SQL planning retains
  `Select(FALSE)` or a limiting operator rather than constructing a zero-row `Values` input to `Exists`.
- The hand-built comparisons for `EliminateDistinctSetLeft`, `EliminateJoinUnderGroupByLeft`, `EliminateJoinUnderGroupByRight`, and
  `EliminateZeroCardSelect` exercise native rules that do fire. Their output shapes differ: native uses
  `DistinctOn` instead of the port's `GroupBy`, retains an identity Project beneath GroupBy, and retains
  the Select over a zero-row input, respectively.
- The hand-built comparisons for `PruneJoinLeftCols`, `PruneJoinRightCols`, `PruneSelectCols`, and
  `PruneSemiAntiJoinRightCols` also trigger native rules. Native narrows a Scan's columns; the ports
  insert explicit Projects. For `PruneProjectCols`, native retains the inner Project while the port
  substitutes its expression into the outer Project.
- `ExtractJoinComparisons` has the same native operator and expression shape, but native comparison allocates
  independent fresh IDs for its two computed columns.
- `PushAggDistinctIntoGroupBy` uses GroupBy for the inner deduplication, while native uses DistinctOn. The SQL
  builder computes a direct aggregate argument before its grouping keys; a subquery with keys followed by the
  argument produces the port's computed-key order and fires the generated rule.
- The remaining hand-built comparisons added for set, barrier, scalar, pruning, and ordinality ports
  either match native output or confirm that the native rule does not fire on the port's structural
  pattern. Each comparison enables only the corresponding native rule and checks the result against
  the hand-built RuleScript expectation.
- `TryDecorrelateProjectSelect` models `LeftJoin`, while the native rule matches `LeftJoinApply`.
- `CommuteRightJoin` retains the explicit output Project in RuleScript, which is unnecessary in CockroachDB's
  column-ID representation.
- `PruneScanCols` has a RuleScript relation symbol where the native rule matches a `Scan`; its SQL test uses a
  three-column `Values` relation to avoid CockroachDB's hidden scan columns.
- `PushFilterIntoSetOp` removes the outer filter, while the native rule leaves a `Select(TRUE)` wrapper until
  another normalization rule removes it.
- `EliminateDistinct` and `EliminateUpsertDistinct` model the native DistinctOn variants as GroupBy, so they fire
  on a different operator shape.
- `ConvertGroupByToDistinct` builds a self-Intersect for distinct grouping tuples in RuleScript; native builds a
  DistinctOn operator.
- `ReduceNotNullGroupingCols` represents duplicate grouping fields with a Project alias over GroupBy; native
  matches UpsertDistinctOn. SQL planning deduplicates the identical grouping expressions before this port can
  match, so its native comparison uses hand-built optimizer expressions.
- `EliminateAggDistinct` requires a unique input in RuleScript, while the native rule matches idempotent aggregate
  functions. `EliminateAggFilteredDistinctForKeys` omits the native `AggFilter` wrapper.
- `HoistSelectAboveUnorderedDistinctOn` models native DistinctOn as GroupBy.
- `CommuteConst` commutes two columns in RuleScript; native CockroachDB requires a constant left operand.
- `CommuteConstInequality` and `CommuteVarInequality` also compare two columns in RuleScript, while the native
  rules require a constant or non-variable left operand, respectively.
- `InlineConstVar`, `InlineProjectConstants`, and `InlineSelectConstants` use equality to a unique relation in
  RuleScript where the native rules inline literal constant values.
- `InlineJoinConstantsLeft` and `InlineJoinConstantsRight` likewise model the constant as a unique one-column
  relation joined on equality; native inlines literal constants from input constraints.
- `AssociateLimitJoinsLeft` and `AssociateLimitJoinsRight` omit the native Limit/ordering wrapper. The two
  `LeftAssociateJoins*` and two `RightAssociateJoins*` ports use fixed single-column conjunct patterns and,
  where needed, an explicit output Project; native join association applies its own bound-condition analysis.
- `TryDecorrelateInnerJoin`, `TryDecorrelateInnerLeftJoin`, `TryDecorrelateProjectInnerJoin`, and
  `TryDecorrelateProjectSet` model the native Apply or ProjectSet shapes as ordinary joins, so their SQL
  comparison plans differ from the native rules.
- `PushSelectCondLeftIntoJoinLeftAndRight` removes the outer Select in RuleScript; native retains it while it
  extracts and maps one matching filter item.
- `SimplifyIntersectLeft` and `SimplifyIntersectRight` build a SemiJoin in RuleScript; native uses IntersectAll.
- `FoldNotFalse` removes the outer Select immediately; native leaves `Select(TRUE)` for `EliminateSelect`.
- `FoldUnary` covers boolean `NOT TRUE` in RuleScript, whereas native `FoldUnary` does not fire on that shape.
- `FoldComparisonWithAny` uses a scalar OR of constant comparisons in RuleScript; native matches an `AnyScalar`.
- `NegateOr` writes a conjunction as two filter items, while native retains one scalar And expression.
- `SimplifyOrTrue` and `SimplifyTrueOr` remove the outer Select immediately; native leaves `Select(TRUE)` for
  `EliminateSelect`.
- `FoldBinary` proves only `TRUE AND FALSE`; the native fold does not fire on that boolean shape.
- `FoldNullUnary` matches the typed `CAST(NULL AS INT)` beneath unary minus in SQL plans; the native rule requires
  a direct `Null` child. `RejectNullsProject` applies its proved nullable-column filter without the native
  `RejectNullCols` guard, so it fires on the comparison query where the native rule does not.
- `FoldNullInNonEmpty` expands membership into a two-term scalar Or of equality checks in RuleScript; native
  matches an `In` or `NotIn` scalar with a nonempty tuple. The native comparison test builds both shapes directly.
- `FoldIsNull` removes the enclosing Select, while native leaves `Select(TRUE)`. `FoldNotNull` and `FoldNullAndOr`
  match typed NULL casts in SQL plans, while their native rules require direct `Null` operands.
- `FoldNullTupleIsTupleNull` and `FoldNullTupleIsTupleNotNull` use separate checks in RuleScript; native matches
  tuple scalar operators. `SimplifySameVarInequalities` emits separate filter items for its conjunction, while
  native keeps one scalar And. `SimplifySelectFilters` removes the NULL disjunct in a filter, while native retains
  the scalar Or in the comparison query.
- `SimplifyRange` models a Range wrapper as `input AND TRUE`; native `SimplifyRange` requires a Range scalar.
- `PushLeakproofFiltersIntoPermeableBarrier` models a one-column Barrier as an identity Project and omits the
  leakproofness flag; the native rule matches a Barrier instead.
- `FoldNonNullIsNull` and `FoldNonNullIsNotNull` use non-nullable relation columns in RuleScript, while native
  scalar folding requires a non-NULL constant. The two tuple variants likewise model element nullability as
  separate column checks, while native matches one tuple scalar.
- `SimplifyIsNullCondition` and `RemoveNotNullCondition` remove an inner Select in their RuleScript after-pattern;
  native changes only the matching outer filter item and retains the inner Select.
- `RejectNullsUnderJoinLeft`, `RejectNullsUnderJoinRight`, `RemoveJoinNotNullCondition`, and
  `SimplifyJoinNotNullEquality` consume a scalar `And` tree in these SQL comparisons. The native rules leave that
  tree in place, while the RuleScript ports flatten and rewrite it.
- `DeduplicateSelectFilters` removes a repeated predicate across three nested Selects in RuleScript. Native
  `DeduplicateSelectFilters` removes duplicate filter items within one Select.
- `ExtractRedundantConjunct` emits two filter items for its factored conjunction; the native rule keeps one
  scalar And expression.
- `NormalizeInConst` models repeated `IN` tuple elements as a four-term disjunction over relation columns;
  native `NormalizeInConst` matches an `In` scalar with constant tuple elements.
- The ports of `SortFiltersInJoin`, `MapEqualityIntoJoinLeftAndRight`, `MapFilterIntoJoinLeft`,
  `MapFilterIntoJoinRight`, `PushFilterIntoJoinLeft`, `PushFilterIntoJoinRight`,
  `PushFilterIntoJoinLeftAndRight`, `InlineSelectVirtualColumns`, `PushSelectIntoInlinableProject`,
  `PushSelectIntoProject`, `PushSelectIntoGroupBy`, `PushSelectIntoUnorderedDistinctOn`,
  `PushSelectIntoJoinLeft`, and `PushSelectIntoProjectSet` also match a scalar `And` tree in one filter item.
  On the comparison queries, the native rules leave that scalar tree in place, while these ports consume its
  leaves as separate filters and apply their proved rewrite.

All 182 currently generate and pass the CockroachDB runtime suite. All have hand-built rewrite and near-miss tests plus an
entry in `TestMatchesNativeRules`; 147 have a positive SQL `planFor` test. Extend this README, the
generator, `Runtime/` and the tests together when adding constructs. CockroachDB's operators are
defined in `pkg/sql/opt/ops/relational.opt`.

| RuleScript | CockroachDB |
|---|---|
| `JoinWithSeparateConds`, `JoinWithPushedConds` | an inner join |
| `Correlate` | `InnerJoinApply`, `LeftJoinApply`, ... |
| `Or`, `Not`, `False`, `JoinField` | the scalar operators; a column of the concatenated left and right columns |
| rules built from Java lambdas | unsupported |

## Generator requirements

The generator works from the structure of the `before()`/`after()` patterns only. It must not branch on a rule's name or
class, and the Go code must not mention any rule. A construct without a translation must make generation of that rule fail
with a message naming the construct; the generator never writes a partial or approximate rule.
`scripts/check-no-hardcoding-cockroach.sh` enforces this for the generator and `Runtime/` and runs in
`test-cockroach.sh`.

## Writing Tests

A test is a Go file in `Tests/` named `<rule>_test.go`; the Bazel target globs `*_test.go`. For each rule it should have:

1. A rewrite test: build the input plan with CockroachDB's constructors (`newEnv`, `scan`, `filters` in
   `helpers_test.go`), build the expected plan by hand, and compare with `expectRewrite`. Do not compute the expected plan
   from the rule.
2. Near-miss tests that must not be rewritten (`expectNoMatch`): another join type, an extra projection or passthrough
   column, an operator in between, a condition on a column outside the symbol's arguments.
3. A query test with `planFor`, which plans SQL with every CockroachDB rule off except the rule under test, when SQL can
   produce the pattern.
4. An entry in `TestMatchesNativeRules` (`native_rules_test.go`) when CockroachDB already has the same rewrite in
   `pkg/sql/opt/norm/rules` or `xform/rules`. Compare plans directly when the patterns agree; document and test
   differences where the proved RuleScript port has different matching guards or a different plan shape. Fresh
   computed column IDs can be normalized for comparison.

Things that went wrong before: plans built by hand need `f.DisableOptimizations()` (done in `newEnv`) or CockroachDB's
own rules rewrite them as you build; `memo.FormatExpr` crashes on such plans, so use `env.format`; a scan built from SQL
carries hidden system columns, so `SELECT *` adds a Project that is not an identity until the `Prune*` rules run (see
`TestSQLMergeSelects`); with the other rules off, `a AND b` stays one filter holding an `And`.
