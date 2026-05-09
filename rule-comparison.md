# RuleScript vs DataFusion: Optimizer Rule Comparison

This document compares RuleScript's declarative rule implementations with DataFusion's imperative optimizer rules.

## Summary

| Metric | RuleScript | DataFusion |
|--------|------------|------------|
| **Total implementation lines** | **432** | **11,392** |
| Equivalent functionality coverage | ~377 lines | ~2,500 lines |
| Unique rules | 55 lines | 8,353 lines |
| **Code reduction for equivalent rules** | **~6.6x more concise** | - |

**Key Finding**: For rules with equivalent functionality, RuleScript averages **6-7x fewer lines of code**. The DSL approach enables expressing the same transformations much more concisely.

## RuleScript Rules (432 lines total, excluding tests)

| Rule File | Lines | Description |
|-----------|-------|-------------|
| filter_aggregate_transpose.rs | 26 | Push filter below aggregate (HAVING optimization) |
| filter_into_join.rs | 20 | Merge filter predicate into join condition |
| filter_merge.rs | 16 | Merge consecutive filters with AND |
| filter_project_transpose.rs | 23 | Push filter below projection |
| filter_reduce_false.rs | 13 | `Filter(_, false)` → `Empty` |
| filter_reduce_true.rs | 13 | `Filter(_, true)` → remove filter |
| join_associate.rs | 25 | `(A ⋈ B) ⋈ C` → `A ⋈ (B ⋈ C)` |
| join_commute.rs | 20 | `A ⋈ B` → `B ⋈ A` with projection |
| join_condition_push.rs | 52 | Push single-table predicates from join to filters |
| join_extract_filter.rs | 19 | Extract join condition to filter above |
| join_project_transpose.rs | 50 | Transpose projection and join |
| project_merge.rs | 19 | Merge consecutive projections |
| project_remove.rs | 14 | Remove identity projections |
| prune_empty_filter.rs | 15 | `Filter(Empty, _)` → `Empty` |
| prune_empty_project.rs | 15 | `Project(Empty, _)` → `Empty` |
| prune_empty_union.rs | 44 | Prune empty branches from union |
| semi_join_filter_transpose.rs | 48 | Transpose filter and semi-join |

## DataFusion Rules (11,392 lines total, excluding tests)

| Rule File | Lines | Description |
|-----------|-------|-------------|
| push_down_filter.rs | 1,424 | Unified filter pushdown (handles many cases) |
| optimize_projections/ | 1,160 | Projection optimization and removal |
| common_subexpr_eliminate.rs | 812 | Common subexpression elimination |
| decorrelate.rs | 626 | General decorrelation utilities |
| decorrelate_predicate_subquery.rs | 467 | Decorrelate predicate subqueries |
| eliminate_cross_join.rs | 444 | Convert cross joins to inner joins |
| scalar_subquery_to_join.rs | 406 | Convert scalar subqueries to joins |
| simplify_expressions/ | 3,895 | Expression simplification |
| eliminate_outer_join.rs | 303 | Convert outer join to inner when safe |
| single_distinct_to_groupby.rs | 279 | Convert DISTINCT to GROUP BY |
| push_down_limit.rs | 270 | Push LIMIT down the tree |
| extract_equijoin_predicate.rs | 269 | Extract equijoin conditions |
| propagate_empty_relation.rs | 232 | Propagate empty relations |
| replace_distinct_aggregate.rs | 205 | Optimize distinct aggregates |
| decorrelate_lateral_join.rs | 143 | Decorrelate lateral joins |
| eliminate_duplicated_expr.rs | 117 | Remove duplicate expressions |
| eliminate_group_by_constant.rs | 114 | Simplify GROUP BY with constants |
| eliminate_nested_union.rs | 113 | Flatten nested unions |
| filter_null_join_keys.rs | 107 | Add null filters for join keys |
| eliminate_limit.rs | 89 | Remove unnecessary LIMIT |
| eliminate_filter.rs | 79 | Remove `Filter(_, true/false)` |
| eliminate_join.rs | 74 | Remove unnecessary joins |
| eliminate_one_union.rs | 64 | Remove single-input union |

## Equivalent Rules Comparison

| RuleScript Rule(s) | RS Lines | DataFusion Equivalent | DF Lines | Ratio |
|-------------------|----------|----------------------|----------|-------|
| FilterReduceTrue + FilterReduceFalse | **26** | EliminateFilter | 79 | 3.0x |
| PruneEmpty* (5 rules) | **74** | PropagateEmptyRelation | 232 | 3.1x |
| FilterIntoJoinRule | **20** | EliminateCrossJoin | 444 | 22.2x |
| JoinConditionPush (L+R) | **52** | PushDownFilter (part) | ~200 | 3.8x |
| FilterProjectTransposeRule | **23** | PushDownFilter (part) | ~150 | 6.5x |
| FilterAggregateTransposeRule | **26** | PushDownFilter (part) | ~100 | 3.8x |
| SemiJoinFilterTranspose (L+R) | **48** | PushDownFilter (part) | ~100 | 2.1x |
| ProjectMerge + ProjectRemove | **33** | OptimizeProjections | 1,160 | 35.2x |
| JoinProjectTranspose (L+R) | **50** | OptimizeProjections (part) | ~200 | 4.0x |
| JoinAssociateRule | **25** | EliminateCrossJoin (implicit) | ~50 | 2.0x |

## Rules Unique to RuleScript

| Rule | Lines | Purpose |
|------|-------|---------|
| JoinCommuteRule | 20 | Explicit join commutativity for plan enumeration |
| JoinExtractFilterRule | 19 | Inverse of FilterIntoJoin for exploration |
| FilterMergeRule | 16 | Explicit filter merging (DF does this implicitly) |

## Rules Unique to DataFusion

| Rule | Lines | Purpose |
|------|-------|---------|
| SimplifyExpressions | 3,895 | Constant folding, boolean simplification, etc. |
| CommonSubexprEliminate | 812 | CSE optimization |
| Decorrelate* (3 rules) | 1,236 | Subquery decorrelation |
| ScalarSubqueryToJoin | 406 | Convert scalar subqueries |
| PushDownLimit | 270 | Limit pushdown optimization |
| ExtractEquijoinPredicate | 269 | Extract equijoin conditions |
| EliminateOuterJoin | 303 | Outer → inner join conversion |
| SingleDistinctToGroupBy | 279 | DISTINCT optimization |
| ReplaceDistinctAggregate | 205 | Distinct aggregate optimization |
| EliminateNestedUnion | 113 | Flatten union trees |
| Other elimination rules | ~400 | Various simplifications |

## Architectural Differences

### RuleScript Approach
- **Small, composable rules**: Each transformation is a separate 15-50 line rule
- **Declarative DSL**: Pattern matching with `from:` and `to:` clauses
- **Incremental**: Apply one transformation at a time
- **Verifiable**: Rules can be formally verified for correctness

### DataFusion Approach
- **Monolithic rules**: Single rules handle many cases (e.g., PushDownFilter: 1,424 lines)
- **Imperative code**: Manual tree traversal and transformation
- **Holistic**: Process entire plan in one pass
- **Performance-optimized**: Fewer passes over the plan tree

## Key Insights

1. **RuleScript achieves ~6.6x code reduction** for equivalent transformations due to the declarative DSL

2. **DataFusion bundles related transformations** into single rules (e.g., all filter pushdown logic in one 1,424-line file vs RuleScript's 5 separate rules totaling ~140 lines)

3. **DataFusion has extensive functionality RuleScript doesn't cover**:
   - Expression simplification (3,895 lines)
   - Subquery decorrelation (1,236 lines)  
   - Common subexpression elimination (812 lines)

4. **RuleScript's unique rules** (JoinCommute, JoinExtract) are useful for plan space exploration but aren't needed in DataFusion's heuristic optimizer

5. **Integration consideration**: When RuleScript rules run alongside DataFusion's optimizer, most produce identical final plans because DataFusion's rules are more comprehensive
