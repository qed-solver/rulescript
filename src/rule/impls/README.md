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
- **Status**: Fully working with tests
- **Notes**: Uses smart column pattern matching - a single column pattern matches the full ordered list of columns. Only matches true identity projections (all columns in same order).

### ProjectMergeRule ✅
- **File**: `project_merge.rs`
- **Pattern**: `Project(g, Project(f, source))` → `Project(g∘f, source)`
- **Status**: Fully working with 3 passing tests
- **Tests**: `test_project_merge_calcite_style`, `test_project_merge_with_column_rename`, `test_no_match_single_projection`
- **Notes**: Successfully merges consecutive projections using function composition. Works with concrete DataFusion plans.

## In Progress 🚧

### Transpose Rules (FilterProjectTranspose, ProjectFilterTranspose)
- **Status**: Rule encoding in progress
- **Note**: These rules require careful encoding of patterns and templates to properly handle column references and schema changes across operators. Implementation will be added after proper rule definition.

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
- **Total Tests**: 11
- **Status**: All passing ✅
- **FilterMergeRule**: 3 tests
- **ProjectRemoveRule**: 5 tests
- **ProjectMergeRule**: 3 tests

## Adding New Rules

When adding a new rule:
1. Create a new file in `impls/` directory
2. Implement `RewriteRule` trait with pattern and replacement
3. Add minimal tests (avoid async/tokio)
4. Update this README with implementation status
5. Consider if rule needs abstract functions for matching