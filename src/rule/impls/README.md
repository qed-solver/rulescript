# RuleScript Rule Implementations

This directory contains concrete implementations of query optimization rules, inspired by Apache Calcite's rule set.

## Implemented Rules

### FilterMergeRule
- **File**: `filter_merge.rs`
- **Pattern**: `Filter(P, Filter(Q, source))` → `Filter(P AND Q, source)`
- **Status**: Fully working with 3 passing tests
- **Notes**: Combines two consecutive filters using AND operator.

### FilterProjectTransposeRule
- **File**: `filter_project_transpose.rs`
- **Pattern**: `Filter(P(y), Project(f(x), source))` → `Project(f(x), Filter(P(f(x)), source))`
- **Status**: Fully working with 6 passing tests
- **Notes**: Pushes filter below projection by rewriting filter predicate to reference source columns.

### FilterAggregateTransposeRule
- **File**: `filter_aggregate_transpose.rs`
- **Pattern**: `Filter(GroupCond(g) ∧ AggCond(g, a), Aggregate(G, A, source))` → `Filter(AggCond(g, a), Aggregate(G, A, Filter(GroupCond(g), source)))`
- **Status**: Fully working with 4 passing tests
- **Notes**: Pushes filter predicates on GROUP BY columns below aggregate. Predicates on aggregate results stay above.

### FilterIntoJoinRule
- **File**: `filter_into_join.rs`
- **Pattern**: `Filter(pred, Join(Inner, cond, L, R))` → `Join(Inner, AND(cond, pred), L, R)`
- **Status**: Fully working with 3 passing tests
- **Notes**: Merges filter above join into join condition.

### ProjectRemoveRule
- **File**: `project_remove.rs`
- **Pattern**: `Project([col], source)` → `source`
- **Status**: Fully working with 6 passing tests
- **Notes**: Removes identity projections (all columns in same order). Generic over source type.

### ProjectMergeRule
- **File**: `project_merge.rs`
- **Pattern**: `Project(g, Project(f, source))` → `Project(g∘f, source)`
- **Status**: Fully working with 3 passing tests
- **Notes**: Merges consecutive projections using function composition.

### JoinCommuteRule
- **File**: `join_commute.rs`
- **Pattern**: `Join(Inner, P(l, r), left, right)` → `Project([l, r], Join(Inner, P(r, l), right, left))`
- **Status**: Fully working with 3 passing tests
- **Notes**: Swaps join inputs and adds projection to preserve column order.

### JoinLeftConditionPushRule
- **File**: `join_left_condition_push.rs`
- **Pattern**: `Join(Inner, LeftCond(l) ∧ JoinCond(l, r), L, R)` → `Join(Inner, JoinCond(l, r), Filter(LeftCond(l), L), R)`
- **Status**: Fully working with 4 passing tests
- **Notes**: Pushes left-table predicates down as filter on left input.

### JoinRightConditionPushRule
- **File**: `join_right_condition_push.rs`
- **Pattern**: `Join(Inner, RightCond(r) ∧ JoinCond(l, r), L, R)` → `Join(Inner, JoinCond(l, r), L, Filter(RightCond(r), R))`
- **Status**: Fully working with 4 passing tests
- **Notes**: Pushes right-table predicates down as filter on right input.

### JoinExtractFilterRule
- **File**: `join_extract_filter.rs`
- **Pattern**: `Join(Inner, condition, L, R)` → `Filter(condition, Join(Inner, TRUE, L, R))`
- **Status**: Fully working with 4 passing tests
- **Notes**: Extracts join condition as filter above cartesian product. Inverse of FilterIntoJoin.

### JoinLeftProjectTransposeRule
- **File**: `join_left_project_transpose.rs`
- **Pattern**: `Join(Inner, P(l', r), Project(f(l), left), right)` → `Project(f(l), r, Join(Inner, P(f(l), r), left, right))`
- **Status**: Fully working with 3 passing tests
- **Notes**: Pulls projection from left input up above the join. Rewrites join condition.

### JoinRightProjectTransposeRule
- **File**: `join_right_project_transpose.rs`
- **Pattern**: `Join(Inner, P(l, r'), left, Project(f(r), right))` → `Project(l, f(r), Join(Inner, P(l, f(r)), left, right))`
- **Status**: Fully working with 3 passing tests
- **Notes**: Pulls projection from right input up above the join. Rewrites join condition.

### JoinAssociateRule
- **File**: `join_associate.rs`
- **Pattern**: `(Q0 ⋈[P0(x,y)] Q1) ⋈[P1(y,z)] Q2` → `Q0 ⋈[P0(x,y)] (Q1 ⋈[P1(y,z)] Q2)`
- **Status**: Fully working with 3 passing tests
- **Notes**: Changes join tree shape using associativity. Only applies to INNER joins.

### LeftSemiJoinFilterTransposeRule
- **File**: `left_semi_join_filter_transpose.rs`
- **Pattern**: `LeftSemiJoin(Filter(P, left), right, C)` → `Filter(P, LeftSemiJoin(left, right, C))`
- **Status**: Fully working with 5 passing tests
- **Notes**: Pushes filter through left semi-join. Filter only references left input columns.

## Test Summary
- **Total Tests**: 57
- **Status**: All passing
- **FilterMergeRule**: 3 tests
- **FilterProjectTransposeRule**: 6 tests
- **FilterAggregateTransposeRule**: 4 tests
- **FilterIntoJoinRule**: 3 tests
- **ProjectRemoveRule**: 6 tests
- **ProjectMergeRule**: 3 tests
- **JoinCommuteRule**: 3 tests
- **JoinLeftConditionPushRule**: 4 tests
- **JoinRightConditionPushRule**: 4 tests
- **JoinExtractFilterRule**: 4 tests
- **JoinLeftProjectTransposeRule**: 3 tests
- **JoinRightProjectTransposeRule**: 3 tests
- **JoinAssociateRule**: 3 tests
- **LeftSemiJoinFilterTransposeRule**: 5 tests

## Adding New Rules

When adding a new rule:
1. Create a new file in `impls/` directory
2. Implement using the `rule!` macro
3. Add comprehensive tests
4. Update this README
5. Export from `mod.rs`
