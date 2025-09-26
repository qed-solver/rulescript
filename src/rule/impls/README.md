# RuleScript Rule Implementations

This directory contains concrete implementations of query optimization rules, inspired by Apache Calcite's rule set.

## Implemented Rules

### FilterMergeRule ✅
- **File**: `filter_merge.rs`
- **Pattern**: `Filter(P, Filter(Q, source))` → `Filter(P AND Q, source)`
- **Test Source**: Custom (Calcite doesn't have explicit tests for this simple rule)
- **Status**: Implemented
- **Notes**: Combines two consecutive filters using AND operator

### ProjectRemoveRule ⚠️
- **File**: `project_remove.rs`
- **Pattern**: `Project(identity, source)` → `source`
- **Test Source**: Based on Calcite's ProjectRemoveRule concept
- **Status**: Partially Implemented
- **Notes**: Pattern matching for identity projections needs enhancement. The rule structure is complete but requires matcher improvements to detect identity mappings.

### FilterProjectTransposeRule ✅
- **File**: `filter_project_transpose.rs`
- **Pattern**: `Filter(P, Project(f, source))` → `Project(f, Filter(P', source))`
- **Test Source**: Based on Calcite's FilterProjectTransposeRule tests
- **Status**: Implemented
- **Notes**: Pushes filter below projection when the predicate can be rewritten in terms of input columns. Uses function composition to rewrite predicates.

### ProjectFilterTransposeRule ✅
- **File**: `project_filter_transpose.rs`
- **Pattern**: `Project(f, Filter(P, source))` → `Filter(P', Project(f, source))`
- **Test Source**: Custom (inverse of FilterProjectTranspose)
- **Status**: Implemented
- **Notes**: Pulls projection above filter. Less commonly beneficial but enables other optimizations.

### ProjectMergeRule ✅
- **File**: `project_merge.rs`
- **Pattern**: `Project(f, Project(g, source))` → `Project(f∘g, source)`
- **Test Source**: Based on Calcite's ProjectMergeRule
- **Status**: Implemented
- **Notes**: Uses function composition to merge consecutive projections. Handles complex expression substitution.

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

## Adding New Rules

When adding a new rule:
1. Create a new file in `impls/` directory
2. Implement `RewriteRule` trait with pattern and replacement
3. Add tests using utilities from `test.rs`
4. Update this README with implementation status
5. Note the source of test cases (Calcite or custom)