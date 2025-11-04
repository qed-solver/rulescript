# RuleScript Rule Implementations

This directory contains concrete implementations of query optimization rules, inspired by Apache Calcite's rule set.

## Implemented Rules

### FilterMergeRule ✅
- **File**: `filter_merge.rs`
- **Pattern**: `Filter(P, Filter(Q, source))` → `Filter(P AND Q, source)`
- **Status**: Fully working with tests
- **Notes**: Combines two consecutive filters using AND operator. Tests use concrete binary expressions.

### ProjectRemoveRule ✅
- **File**: `project_remove.rs`
- **Pattern**: `Project([col], source)` → `source`
- **Status**: Fully working with 6 passing tests
- **Tests**:
  - `test_project_remove_identity_dept` - Identity projection over table
  - `test_project_remove_identity_emp` - Identity projection over table
  - `test_project_remove_identity_over_join` - Identity projection over join (also serves as ProjectJoinRemoveRule)
  - `test_no_match_reordered` - Negative test (reordered columns)
  - `test_no_match_subset` - Negative test (subset of columns)
  - `test_no_match_with_expressions` - Negative test (expressions)
- **Notes**: Uses smart column pattern matching - a single column pattern matches the full ordered list of columns. Only matches true identity projections (all columns in same order). **Generic over source type** - works with any input (table, join, filter, etc.), so it also serves as ProjectJoinRemoveRule without needing a separate implementation.

### ProjectMergeRule ✅
- **File**: `project_merge.rs`
- **Pattern**: `Project(g, Project(f, source))` → `Project(g∘f, source)`
- **Status**: Fully working with 3 passing tests
- **Tests**: `test_project_merge_calcite_style`, `test_project_merge_with_column_rename`, `test_no_match_single_projection`
- **Notes**: Successfully merges consecutive projections using function composition. Works with concrete DataFusion plans.

### FilterProjectTransposeRule ✅
- **File**: `filter_project_transpose.rs`
- **Pattern**: `Filter(P(y), Project(f(x), source))` → `Project(f(x), Filter(P(f(x)), source))`
- **Status**: Fully working with 6 passing tests
- **Tests**: 
  - `test_filter_project_transpose_basic` - Basic column projection with simple filter
  - `test_filter_project_transpose_multiple_columns` - Filter with AND condition on multiple columns
  - `test_filter_project_transpose_with_expression` - Projection with expression (deptno * 2)
  - `test_filter_project_transpose_subset_columns` - Single column projection
  - `test_filter_project_transpose_complex_predicate` - Filter with OR condition
  - `test_no_match_filter_only` - Negative test
- **Notes**: Pushes filter below projection by composing predicate with projection function. Uses nested function calls `P(f(x))` to rewrite column references through function composition mechanism. Handles both simple column projections and complex expressions.

### JoinCommuteRule ✅
- **File**: `join_commute.rs`
- **Pattern**: `Join(Inner, P(l, r), left, right)` → `Project([l, r], Join(Inner, P(r, l), right, left))`
- **Status**: Fully working with 3 passing tests
- **Notes**: Swaps join inputs and adds projection to preserve output column order. Field references in join condition are swapped.

### JoinLeftConditionPushRule ✅
- **File**: `join_left_condition_push.rs`
- **Pattern**: `Join(Inner, LeftCond(l) ∧ JoinCond(l, r), L, R)` → `Join(Inner, JoinCond(l, r), Filter(LeftCond(l), L), R)`
- **Status**: Fully working with 4 passing tests
- **Notes**: Pushes left-table predicates only. More flexible than JoinConditionPushRule as it doesn't require right predicates.

### JoinRightConditionPushRule ✅
- **File**: `join_right_condition_push.rs`
- **Pattern**: `Join(Inner, RightCond(r) ∧ JoinCond(l, r), L, R)` → `Join(Inner, JoinCond(l, r), L, Filter(RightCond(r), R))`
- **Status**: Fully working with 4 passing tests
- **Notes**: Pushes right-table predicates only. More flexible than JoinConditionPushRule as it doesn't require left predicates.

### JoinExtractFilterRule ✅
- **File**: `join_extract_filter.rs`
- **Pattern**: `Join(Inner, condition, L, R)` → `Filter(condition, Join(Inner, TRUE, L, R))`
- **Status**: Fully working with 4 passing tests
- **Notes**: Extracts join condition as filter above cartesian product. Inverse of FilterIntoJoin. Enables filter merge opportunities.

### FilterIntoJoinRule ✅
- **File**: `filter_into_join.rs`
- **Pattern**: `Filter(pred, Join(Inner, cond, L, R))` → `Join(Inner, AND(cond, pred), L, R)`
- **Status**: Fully working with 3 passing tests
- **Notes**: Merges filter above join into join condition. Simple predicate merge for inner joins.

### JoinLeftProjectTransposeRule ✅
- **File**: `join_left_project_transpose.rs`
- **Pattern**: `Join(Inner, P(l', r), Project(f(l), left), right)` → `Project(f(l), r, Join(Inner, P(f(l), r), left, right))`
- **Status**: Fully working with 3 passing tests
- **Tests**:
  - `test_join_left_project_transpose_basic` - Basic projection pull-up
  - `test_no_match_no_left_projection` - Negative test (no projection)
  - `test_no_match_right_projection_only` - Negative test (wrong side)
- **Notes**: Pulls projection from left input of inner join up above the join. Rewrites join condition to reference original left columns. Only applies to inner joins on left input.

### JoinRightProjectTransposeRule ✅
- **File**: `join_right_project_transpose.rs`
- **Pattern**: `Join(Inner, P(l, r'), left, Project(f(r), right))` → `Project(l, f(r), Join(Inner, P(l, f(r)), left, right))`
- **Status**: Fully working with 3 passing tests
- **Tests**:
  - `test_join_right_project_transpose_basic` - Basic projection pull-up
  - `test_no_match_no_right_projection` - Negative test (no projection)
  - `test_no_match_left_projection_only` - Negative test (wrong side)
- **Notes**: Pulls projection from right input of inner join up above the join. Rewrites join condition to reference original right columns. Only applies to inner joins on right input. Mirror of JoinLeftProjectTransposeRule.

### JoinAssociateRule ✅
- **File**: `join_associate.rs`
- **Pattern**: `(Q0 ⋈[P0(x,y)] Q1) ⋈[P1(y,z)] Q2` → `Q0 ⋈[P0(x,y)] (Q1 ⋈[P1(y,z)] Q2)`
- **Status**: Fully working with 3 passing tests
- **Tests**:
  - `test_join_associate_basic` - Basic associativity transformation
  - `test_no_match_single_join` - Negative test (single join)
  - `test_no_match_single_table` - Negative test (no join)
- **Notes**: Changes join tree shape using associativity. Only applies to INNER joins. Q1 is the "pivot" table appearing in both joins. Simplified version with 2 predicates (vs 4 predicates in full paper version).

## Future Work 🔮

### ProjectFilterTranspose ⚠️ (Encodable but Limited Matcher Support)
- **Pattern**: `Project([g(x)], Filter(P(x), source(x, y)))` → `Project([g(x)], Filter(P(x), Project([x], source(x, y))))`
- **Status**: Can be encoded, but DefaultMatcher cannot optimize it effectively
- **What it does**: Pulls projection above filter by inserting intermediate projection with only needed columns
- **Limitation**: DefaultMatcher will match everything to `x`, making the intermediate projection identical to source (no optimization benefit)
- **Requires**: Advanced matcher with dependency analysis to determine minimal column set `x` that satisfies both `P` and `g`
- **Note**: Rule is theoretically correct but requires matcher enhancements for practical benefit

### FilterRemoveRule ❌
- **Reason**: Requires constant evaluation to detect `TRUE` predicates
- **Workaround**: Could implement with a simple TRUE literal check, but less useful without constant folding

### Constant Folding Rules ❌
- **Reason**: Explicitly excluded from scope
- **Note**: Would require expression evaluation engine

## Test Utilities

Test utilities are located in `src/rule/test.rs` and provide:
- Helper functions to create test DataFusion plans
- Schema builders for common test scenarios
- Expression builders for predicates and projections
- Plan comparison utilities

## Testing Approach

### Current State
- Tests are minimal and fast (no async, no CSV files, no SessionContext)
- All implemented rules (FilterMerge, ProjectRemove, ProjectMerge) have working end-to-end tests
- Transpose rules require proper encoding - implementation in progress

## Pattern Matching Behavior

### Column Patterns (Abstract Matching)
Column patterns are abstract and match based on field partitions and context:
- **Column patterns match multiple columns**: A single pattern column like `col("a")` can bind to multiple concrete columns [col1, col2, col3] based on the field partition
- **Context determines matching behavior**:
  - In projections: matches ordered sequences (identity projection detection)
  - In function arguments: matches based on dependencies
- **Example**: Pattern `project([col("a")])` where "a" binds to partition [col1, col2, col3] matches concrete `project([col1, col2, col3])`

### Abstract Function Matching
Abstract functions bind to concrete expressions based on dependencies:
- **Pattern**: `P(col("a"))` can match any predicate expression like `salary > 50000` or `deptno = 10`
- **Binding**: Function name (e.g., "P") binds to the entire concrete expression
- **Dependencies**: Matcher validates that concrete expression only uses columns from the pattern's partition

### Alias Handling
- Matcher handles Alias in both pattern and concrete expressions
- Pattern without alias can match concrete with alias (looks through the wrapper)
- Enables matching of aliased projections common in real query plans

## Implementation Status Legend
- ✅ Fully working with end-to-end tests
- ❌ Not supported due to fundamental limitations

## Test Summary
- **Total Tests**: 48
- **Status**: All passing ✅
- **FilterMergeRule**: 3 tests
- **ProjectRemoveRule**: 6 tests (includes ProjectJoinRemoveRule case)
- **ProjectMergeRule**: 3 tests
- **FilterProjectTransposeRule**: 6 tests
- **JoinCommuteRule**: 3 tests
- **JoinLeftConditionPushRule**: 4 tests
- **JoinRightConditionPushRule**: 4 tests
- **JoinExtractFilterRule**: 4 tests
- **FilterIntoJoinRule**: 3 tests
- **JoinLeftProjectTransposeRule**: 3 tests
- **JoinRightProjectTransposeRule**: 3 tests
- **JoinAssociateRule**: 3 tests

## Adding New Rules

When adding a new rule:
1. Create a new file in `impls/` directory
2. Implement `RewriteRule` trait with pattern and replacement
3. Add minimal tests (avoid async/tokio)
4. Update this README with implementation status
5. Consider if rule needs abstract functions for matching