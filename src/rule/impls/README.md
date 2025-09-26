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

### ProjectMergeRule ⚠️
- **File**: `project_merge.rs`
- **Pattern**: `Project(f, Project(g, source))` → `Project(f∘g, source)`
- **Status**: Pattern compiles correctly, but full matching requires abstract functions in concrete plans
- **Notes**: Uses function composition to merge consecutive projections. Pattern uses aliased outputs for proper column referencing.

### FilterProjectTransposeRule ⚠️
- **File**: `filter_project_transpose.rs`
- **Pattern**: `Filter(P(f(x)), Project(f(x), source))` → `Project(f(x), Filter(P(x), source))`
- **Status**: Pattern compiles correctly, but full matching requires abstract functions
- **Notes**: Pushes filter below projection. Would require predicate rewriting in real implementation.

### ProjectFilterTransposeRule ⚠️
- **File**: `project_filter_transpose.rs`
- **Pattern**: `Project(f(x), Filter(P(x), source))` → `Filter(P(f(x)), Project(f(x), source))`
- **Status**: Pattern compiles correctly, but full matching requires abstract functions
- **Notes**: Pulls projection above filter. Would require inverse function mapping in real implementation.

## Not Implemented / Cannot Support

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
- Simple rules (FilterMerge, ProjectRemove) have working end-to-end tests
- Complex rules with abstract functions have pattern compilation tests only

### Pattern vs Concrete Matching
- **Issue**: Patterns use abstract functions (ScalarFunction with UDFs) 
- **Concrete plans**: Use regular expressions (BinaryExpr, Column, etc.)
- **Result**: Abstract functions can't match regular binary expressions
- **Solution**: For full testing, would need to create plans with abstract functions

## Pattern Matching Behavior

### Column Patterns
Column patterns behave differently depending on context:
- **In projections**: A column pattern matches an ordered sequence of columns from its partition
  - Example: Pattern `project([col("a")])` where "a" maps to [col1, col2, col3] matches concrete `project([col1, col2, col3])` exactly
- **In function arguments**: A column pattern matches any single column from its partition
  - Example: Pattern `F(col("a"))` where "a" maps to [col1, col2, col3] can match `F(col2)` or `F(col3)`

### Alias Handling
- Matcher handles Alias in both pattern and concrete expressions
- Pattern without alias can match concrete with alias (looks through the wrapper)
- Enables matching of aliased projections common in real query plans

## Implementation Status Legend
- ✅ Fully working with end-to-end tests
- ⚠️ Pattern compiles but requires abstract functions for full matching
- ❌ Not implemented or has known issues

## Adding New Rules

When adding a new rule:
1. Create a new file in `impls/` directory
2. Implement `RewriteRule` trait with pattern and replacement
3. Add minimal tests (avoid async/tokio)
4. Update this README with implementation status
5. Consider if rule needs abstract functions for matching