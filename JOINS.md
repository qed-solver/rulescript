# Join Rules Analysis

This document catalogs all join-related optimization rules from Apache Calcite that can be implemented with our current support for Join, Filter, and Project plan types.

## Overview

Based on analysis of:
- Calcite codebase: `/home/macronova/桌面/rulescript/calcite/`
- Java RuleScript parser: `/home/macronova/桌面/rulescript/parser/`
- 47 join-related rule files found in Calcite
- 6 verified rules in parser, 6 unprovable rules

---

## Category 1: Core Join Transformation Rules

### 1. JoinCommuteRule

**Calcite Implementation**: `JoinCommuteRule.java`

**What it does**: Permutes the inputs to a join, swapping left and right sides.

**Pattern**: 
```
Join(type, condition, Left, Right) 
→ 
Project(reorder_columns, Join(type, condition_swapped, Right, Left))
```

**Key Details**:
- Adds a projection to preserve output column order after swap
- Configurable via `swapOuter` flag:
  - `false` (default): Only inner joins
  - `true`: Allows outer joins with appropriate type transformations (LEFT ↔ RIGHT)
- Join condition field references must be adjusted (position 0 ↔ position 1)
- Uses `RexPermuteInputsShuttle` to rewrite condition expressions

**Java RuleScript**: `RRuleInstances/JoinCommute.java` ✅ **Verified**

**Abstract Form**:
```java
// Before
left.join(JoinRelType.INNER, pred, right)

// After  
right.join(JoinRelType.INNER, swappedPred, left) + ProjectionRelRN
// where swappedPred swaps field references: field(0) ↔ field(1)
// and ProjectionRelRN reorders output columns back
```

**Calcite Tests**: `RelOptRulesTest.java`
- `testSwapJoin()` - Basic inner join swap
- `testSwapOuterJoin()` - Left/Right join swap when enabled
- Tests verify column ordering is preserved

**Complexity**: Medium
- Requires projection for schema compatibility
- Field reference rewriting in join condition
- Join type transformation for outer joins

**Value**: High - Enables other optimizations by reordering join tree

---

### 2. JoinAssociateRule

**Calcite Implementation**: `JoinAssociateRule.java`

**What it does**: Changes join tree shape using associativity: `((A JOIN B) JOIN C)` → `(A JOIN (B JOIN C))`

**Pattern**:
```
Join(τ1, P1, Join(τ0, P0, A, B), C)
→
Join(τ3, P0', A, Join(τ2, P1', B, C))
```

**Key Details**:
- Only valid for specific join type combinations (16 out of 256 possible)
- Requires careful condition rewriting:
  - Split `P1` into predicates on (A,B), (B,C), and (A,C)
  - Adjust field positions for nested join structure
- Uses `RexPermuteInputsShuttle` for complex field remapping
- Checks for system fields (not supported)
- Validates join types are compatible for transformation

**Java RuleScript**: `RRuleInstances-unprovable/JoinAssociate.java` ⚠️ **Unprovable**

**Abstract Form**:
```java
// Uses meta-variables for join types
MetaJoinType mjt_0, mjt_1, mjt_2, mjt_3

// Before
RelRN before_ab = a.join(mjt_0, 
    AND(pred_ab(a,b), b.field IS NOT NULL), b)
RelRN before = before_ab.join(mjt_1, 
    AND(pred_bc(ab,c), b.field IS NOT NULL), c)

// After  
RelRN after_bc = b.join(mjt_2, 
    AND(pred_bc(b,c), b.field IS NOT NULL), c)
RelRN after = a.join(mjt_3, 
    AND(pred_ab(a,bc), b.field IS NOT NULL), after_bc)

// Generates 256 combinations (4^4), only 16 are provably correct
```

**Calcite Tests**: `RelOptRulesTest.java`
- `testAssociativity()` - Various join type combinations
- Tests validate only correct combinations are applied
- Paper reference: Table 3 lists the 16 valid combinations

**Complexity**: Very High
- 256 possible join type combinations
- Complex field position calculations
- Condition decomposition and rewriting
- Requires IS NOT NULL predicates for correctness

**Value**: Medium - Enables join reordering but extremely complex

**Recommendation**: Defer - Too complex for initial implementation

---

### 3. JoinPushThroughJoinRule

**Calcite Implementation**: `JoinPushThroughJoinRule.java`

**What it does**: Pushes the right input of a join through the left input (also a join), enabling earlier condition application.

**Pattern**:
```
Join(P_BC, Join(P_AB, A, B), C)
→  
Join(P_AB, Join(P_AC, A, C), B)
```

**Example**:
```sql
-- Before
(sales JOIN product_class ON true) JOIN product 
  ON s.product_id = p.product_id AND p.class_id = pc.class_id

-- After
(sales JOIN product ON s.product_id = p.product_id) JOIN product_class
  ON p.class_id = pc.class_id
```

**Key Details**:
- Has LEFT and RIGHT variants (pushes different direction)
- Requires analyzing which conditions reference which inputs
- Decomposes top join condition based on input references
- Recalculates field positions for new join structure
- More restrictive for outer joins (checks null-generating sides)

**Java RuleScript**: Not found in parser

**Abstract Form**: Not available

**Calcite Tests**: `RelOptRulesTest.java`
- `testPushJoinThroughJoin()` - Basic transformation
- Tests show condition splitting and reapplication

**Complexity**: High
- Condition analysis to determine input dependencies  
- Field position recalculation
- Join type compatibility checking

**Value**: High - Enables more efficient join execution order

---

## Category 2: Filter-Join Interaction Rules

### 4. FilterJoinRule (Base class)

**Calcite Implementation**: `FilterJoinRule.java` (abstract base)

**What it does**: Pushes filters above and within join nodes into the join and/or its children. Base class for multiple filter-join rules.

**Variants**:
- **FILTER_INTO_JOIN**: Merge filter above join into join condition
- **JOIN_CONDITION_PUSH**: Push join conditions down as filters on inputs
- **DUMB**: Simple push without optimization analysis

**Key Details**:
- Uses `RelOptUtil.classifyFilters()` to categorize predicates by which input they reference:
  - Above filters (WHERE clause) - can push if not on null-generating side
  - Join filters (ON clause) - can push if not on preserved side
  - Splits into: `leftFilters`, `rightFilters`, `joinFilters`
- Outer join handling via `canPushIntoFromAbove()`, `canPushLeftFromAbove()`, `canPushRightFromAbove()`
- Smart mode: Can simplify outer joins to inner based on pushed filters
- Validates filter pushing doesn't change semantics

**Predicate Classification Logic**:
```java
// For each predicate, determine which tables it references
ImmutableBitSet leftBitSet = ImmutableBitSet.range(0, leftFieldCount);
ImmutableBitSet rightBitSet = ImmutableBitSet.range(leftFieldCount, totalFieldCount);

if (predicate references only left) → leftFilters
else if (predicate references only right) → rightFilters  
else → joinFilters (cross-table)
```

**Java RuleScript**: Base for `FilterIntoJoin` and `JoinConditionPush`

**Calcite Tests**: `RelOptRulesTest.java`
- `testPushFilterPastJoin()` - Various filter push scenarios
- `testPushFilterPastJoinWithAntiJoin()` - Anti-join cases
- `testOuterJoinSimplification()` - Simplifying outer to inner

**Complexity**: High - Base infrastructure for filter-join rules

**Value**: Critical - Foundation for filter optimization

---

### 5. FilterIntoJoin (FilterJoinRule variant)

**Calcite Implementation**: `FilterJoinRule.java` with `FILTER_INTO_JOIN` config

**What it does**: Merges a filter above an inner join into the join condition.

**Pattern**:
```
Filter(outer_pred, Join(Inner, join_pred, L, R))
→
Join(Inner, AND(join_pred, outer_pred), L, R)
```

**Key Details**:
- Only applies to inner joins by default
- Uses predicate pushdown to move filter into ON clause
- Enables further optimizations by consolidating conditions
- Simpler than full FilterJoinRule (no splitting to inputs)

**Java RuleScript**: `RRuleInstances/FilterIntoJoin.java` ✅ **Verified**

**Abstract Form**:
```java
// Before
left.join(JoinRelType.INNER, joinCond, right).filter(outerPred)

// After
left.join(JoinRelType.INNER, AND(joinCond, outerPred), right)
```

**Calcite Tests**: `RelOptRulesTest.java`
- `testPushFilterIntoJoin()` - Basic filter merge
- Validates condition combining preserves semantics

**Complexity**: Low - Simple predicate merge

**Value**: High - Common optimization, enables further rewrites

---

### 6. JoinConditionPush (FilterJoinRule variant)

**Calcite Implementation**: `FilterJoinRule.java` with condition push configuration

**What it does**: Decomposes join condition, pushes single-table predicates as filters below join.

**Pattern**:
```
Join(Inner, AND(cross_cond, left_cond, right_cond), L, R)
→
Join(Inner, cross_cond, Filter(left_cond, L), Filter(right_cond, R))
```

**Key Details**:
- Analyzes join condition to identify:
  - Cross-table predicates (reference both inputs) - stay in join
  - Left-only predicates - push to left input as filter
  - Right-only predicates - push to right input as filter
- Uses `RexUtil.InputFinder` to determine which fields each predicate references
- Major optimization: Filters data before expensive join operation
- Only safe for inner joins (outer joins have different semantics)

**Java RuleScript**: `RRuleInstances/JoinConditionPush.java` ✅ **Verified**

**Abstract Form**:
```java
// Before
left.join(JoinRelType.INNER, 
    AND(crossTableCond(left.field0, right.field0), 
        leftOnlyCond(left.field0),
        rightOnlyCond(right.field0)), 
    right)

// After
filteredLeft = left.filter(leftOnlyCond(left.field0))
filteredRight = right.filter(rightOnlyCond(right.field0))
filteredLeft.join(JoinRelType.INNER, crossTableCond, filteredRight)
```

**Calcite Tests**: `RelOptRulesTest.java`
- `testPushJoinConditionToInputs()` - Condition decomposition
- Tests verify correct predicate classification

**Complexity**: High - Requires predicate analysis and field tracking

**Value**: Very High - Major performance optimization

---

### 7. JoinExtractFilterRule

**Calcite Implementation**: `JoinExtractFilterRule.java` + `AbstractJoinExtractFilterRule.java`

**What it does**: Extracts join condition as a filter above cartesian join. Inverse of FilterIntoJoin.

**Pattern**:
```
Join(Inner, condition, L, R)
→
Filter(condition, Join(Inner, TRUE, L, R))
```

**Key Details**:
- Converts join condition to cartesian product + filter
- Useful for enabling filter merge with other filters above
- Base class `AbstractJoinExtractFilterRule` provides shared logic
- Creates `Join` with `TRUE` literal as condition
- Moves original condition to `Filter` node above

**Java RuleScript**: `RRuleInstances/JoinExtractFilter.java` ✅ **Verified**

**Abstract Form**:
```java
// Before
left.join(JoinRelType.INNER, joinCond, right)

// After
left.join(JoinRelType.INNER, TRUE, right).filter(joinCond)
```

**Calcite Tests**: `RelOptRulesTest.java`
- `testExtractJoinFilter()` - Basic extraction
- Often used in combination with FilterMerge

**Complexity**: Low - Simple condition movement

**Value**: Medium - Enables other optimizations via filter merge

**Use Case**: When you want to merge join condition with WHERE clause filters

---

### 8. JoinDeriveIsNotNullFilterRule

**Calcite Implementation**: `JoinDeriveIsNotNullFilterRule.java`

**What it does**: Infers IS NOT NULL filters from join conditions and pushes them down to inputs.

**Pattern**:
```
Join(Inner, L.x = R.y, L, R)
→
Join(Inner, L.x = R.y, 
     Filter(L.x IS NOT NULL, L), 
     Filter(R.y IS NOT NULL, R))
```

**Key Details**:
- Analyzes equality conditions in join to derive NOT NULL constraints
- Only applies when:
  - Column is used in equi-join condition
  - Column is nullable in schema
  - Adding filter would reduce cardinality
- Uses `RexUtil.findNullableInputRefs()` to identify candidates
- Particularly useful for outer joins (can simplify to inner)
- Works with both ON clause and pushed-down conditions

**Java RuleScript**: Not found in parser

**Abstract Form**: Not available

**Calcite Tests**: `RelOptRulesTest.java`
- `testDeriveNotNullFilter()` - Basic derivation
- `testDeriveNotNullFilterOuterJoin()` - Outer join cases

**Complexity**: Medium - Requires nullability analysis

**Value**: Medium - Optimization opportunity from schema knowledge

---

## Category 3: Project-Join Interaction Rules

### 9. JoinProjectTransposeRule

**Calcite Implementation**: `JoinProjectTransposeRule.java`

**What it does**: Pushes projection below join when projection only uses join output columns.

**Pattern**:
```
Project(exprs, Join(condition, L, R))
→
Join(condition', Project(L_exprs, L), Project(R_exprs, R))
```

**Key Details**:
- Splits projection expressions by which input they reference
- Creates separate projections for left and right inputs
- Adjusts join condition field references for new schema
- Only applies if transformation reduces work (e.g., fewer columns joined)
- Can eliminate projection entirely if it's identity
- More aggressive with configuration option `allowFunctions`

**Java RuleScript**: Not found in parser (similar concept in `ProjectFilterTranspose`)

**Abstract Form**: Not available

**Calcite Tests**: `RelOptRulesTest.java`
- `testPushProjectPastJoin()` - Basic push
- `testPushProjectWithExpressionsJoin()` - Complex expressions

**Complexity**: High - Expression splitting and field remapping

**Value**: High - Reduces join cardinality when projecting early

---

### 10. ProjectJoinTransposeRule

**Calcite Implementation**: `ProjectJoinTransposeRule.java`

**What it does**: Pulls projection from join input above the join, or pushes projection from above through join.

**Pattern (Pull Up)**:
```
Join(condition, Project(L_exprs, L), R)
→
Project(adjusted_exprs, Join(condition', L, R))
```

**Key Details**:
- Has multiple variants:
  - Pull projection from left input above join
  - Pull projection from right input above join  
  - Pull from both inputs
- Adjusts join condition and output projection
- Useful for exposing more optimization opportunities
- Can help with column pruning

**Java RuleScript**: Not explicitly found

**Abstract Form**: Not available

**Calcite Tests**: `RelOptRulesTest.java`
- `testPullProjectFromJoinInput()` - Various scenarios

**Complexity**: High - Bidirectional transformation

**Value**: Medium - Exposes other optimizations

---

### 11. ProjectJoinRemoveRule

**Calcite Implementation**: `ProjectJoinRemoveRule.java`

**What it does**: Removes identity projection above join.

**Pattern**:
```
Project([all_columns_in_order], Join(condition, L, R))
→
Join(condition, L, R)
```

**Key Details**:
- Detects when projection is identity (all columns, same order)
- Similar to `ProjectRemoveRule` but join-specific
- Simplifies plan by removing no-op node

**Java RuleScript**: Similar to `RRuleInstances/PruneEmptyProject.java`

**Abstract Form**: Straightforward identity removal

**Calcite Tests**: `RelOptRulesTest.java`
- `testRemoveIdentityProjectOverJoin()`

**Complexity**: Low - Identity detection

**Value**: Low - Cleanup rule, doesn't enable new optimizations

---

### 12. ProjectJoinJoinRemoveRule

**Calcite Implementation**: `ProjectJoinJoinRemoveRule.java`

**What it does**: Removes redundant projections in multi-join trees.

**Pattern**:
```
Project(cols, Join(c1, Join(c2, L, R1), R2))
→
Join(c1, Join(c2, L, R1), R2)  [if projection is redundant]
```

**Key Details**:
- Analyzes projection in context of join tree
- More sophisticated than simple identity check
- Considers which columns are actually used downstream

**Java RuleScript**: Not found

**Abstract Form**: Not available

**Calcite Tests**: `RelOptRulesTest.java`
- `testRemoveProjectFromJoinTree()`

**Complexity**: Medium - Multi-level analysis

**Value**: Low - Specific cleanup scenario

---

## Category 4: Join Condition Manipulation

### 13. JoinPushExpressionsRule

**Calcite Implementation**: `JoinPushExpressionsRule.java`

**What it does**: Pushes complex expressions in join conditions into projections on inputs.

**Pattern**:
```
Join(L.f(x) = R.g(y), L, R)
→
Join(L'.fx = R'.gy, Project([x, f(x) as fx], L), Project([y, g(y) as gy], R))
```

**Key Details**:
- Extracts non-trivial expressions from join condition
- Creates projections that compute expressions beforehand
- Simplifies join condition to simple column comparisons
- Enables better join algorithm selection (e.g., hash join)
- Only beneficial if expression is expensive

**Java RuleScript**: Not found

**Abstract Form**: Not available

**Calcite Tests**: `RelOptRulesTest.java`
- `testPushExpressionsInJoin()` - Expression extraction

**Complexity**: High - Expression extraction and projection creation

**Value**: Medium - Enables better join algorithms

---

### 14. JoinPushTransitivePredicatesRule

**Calcite Implementation**: `JoinPushTransitivePredicatesRule.java`

**What it does**: Derives transitive predicates from join conditions and equality constraints.

**Pattern**:
```
Join(Inner, L.x = R.y AND R.y = 5, L, R)
→
Join(Inner, L.x = R.y AND R.y = 5, Filter(L.x = 5, L), Filter(R.y = 5, R))
```

**Key Details**:
- Analyzes equalities in join condition: `a = b AND b = c` → `a = c`
- Analyzes constants: `a = b AND b = 5` → `a = 5`
- Pushes derived predicates to inputs as filters
- Uses `RelMdPredicates` to infer predicates
- Enables earlier filtering of data
- Works across multiple joins in tree

**Java RuleScript**: `RRuleInstances/JoinPushTransitivePredicates.java` ✅ **Verified**

**Abstract Form**: 
```java
// Derives transitive predicates from equalities
// If L.x = R.y and constant pred on R.y, derives constant pred on L.x
```

**Calcite Tests**: `RelOptRulesTest.java`
- `testTransitiveInference()` - Various transitivity cases
- `testTransitiveInferenceWithConstants()` - Constant propagation

**Complexity**: High - Requires equality graph analysis

**Value**: High - Major optimization via predicate inference

---

### 15. JoinConditionExpandIsNotDistinctFromRule

**Calcite Implementation**: `JoinConditionExpandIsNotDistinctFromRule.java`

**What it does**: Expands `IS NOT DISTINCT FROM` operator in join conditions to handle NULL semantics explicitly.

**Pattern**:
```
Join(L.x IS NOT DISTINCT FROM R.y, L, R)
→
Join((L.x = R.y) OR (L.x IS NULL AND R.y IS NULL), L, R)
```

**Key Details**:
- `IS NOT DISTINCT FROM` treats NULL = NULL as true (unlike regular `=`)
- Expands to explicit NULL handling for systems that don't support it
- Enables further optimization of the expanded form
- Useful for databases with limited NULL handling

**Java RuleScript**: Not found

**Abstract Form**: Not available

**Calcite Tests**: `RelOptRulesTest.java`
- `testExpandIsNotDistinctFrom()` - NULL handling

**Complexity**: Low - Straightforward expansion

**Value**: Low - Compatibility/edge case handling

---

### 16. ExpandDisjunctionForJoinInputsRule

**Calcite Implementation**: `ExpandDisjunctionForJoinInputsRule.java`

**What it does**: Expands disjunctions (OR) in join conditions to enable better optimization.

**Pattern**:
```
Join((L.x = 1 AND R.y = 2) OR (L.x = 3 AND R.y = 4), L, R)
→
[More optimizable form, possibly with unions]
```

**Key Details**:
- Converts OR conditions to allow filter pushdown
- May create union of joins in some cases
- Enables derivation of filters: `(L.x = 1 OR L.x = 3)` → `L.x IN (1,3)`
- Complex transformation depending on OR structure

**Java RuleScript**: Not found

**Abstract Form**: Not available

**Calcite Tests**: `RelOptRulesTest.java`
- `testExpandOrInJoin()` - Disjunction cases

**Complexity**: Very High - OR expansion logic

**Value**: Medium - Edge case optimization

---

## Rules NOT Implementable (Missing Dependencies)

### Excluded - Require Additional Plan Types

**SemiJoin Rules** (Need SemiJoin support):
- `SemiJoinRule.java` - Convert to semi-join
- `SemiJoinFilterTransposeRule.java`
- `SemiJoinProjectTransposeRule.java`
- `SemiJoinJoinTransposeRule.java`
- `SemiJoinRemoveRule.java`
- `JoinAddRedundantSemiJoinRule.java`
- `IntersectToSemiJoinRule.java` (needs Intersect)
- `MinusToAntiJoinRule.java` (needs Minus)

**Aggregate-Join Rules** (Need Aggregate support):
- `AggregateJoinTransposeRule.java`
- `AggregateJoinRemoveRule.java`
- `AggregateJoinJoinRemoveRule.java`
- `AggregateProjectConstantToDummyJoinRule.java`

**Set Operation Rules** (Need Union/Intersect/Minus):
- `JoinUnionTransposeRule.java` 
- `FullToLeftAndRightJoinRule.java` (converts to union)
- `JoinExpandOrToUnionRule.java` (creates union)

**Sort Rules** (Need Sort support):
- `SortJoinTransposeRule.java`
- `SortJoinCopyRule.java`

**MultiJoin Rules** (Need MultiJoin plan node):
- `JoinToMultiJoinRule.java`
- `MultiJoinOptimizeBushyRule.java`
- `MultiJoinProjectTransposeRule.java`
- `FilterMultiJoinMergeRule.java`
- `ProjectMultiJoinMergeRule.java`
- `LoptOptimizeJoinRule.java`

**Other Dependencies**:
- `JoinToCorrelateRule.java` (needs Correlate)
- `JoinToHyperGraphRule.java` (needs HyperGraph)
- `DphypJoinReorderRule.java` (needs special cost model)

---

## Implementation Roadmap

### Phase 1: Core Verified Rules (High Priority)

1. **JoinCommuteRule** ✅ Verified
   - Difficulty: Medium (needs projection for column reordering)
   - Value: High (enables other optimizations)
   - Dependencies: None

2. **FilterIntoJoin** ✅ Verified  
   - Difficulty: Low (simple predicate merge)
   - Value: High (common optimization)
   - Dependencies: None

3. **JoinExtractFilter** ✅ Verified
   - Difficulty: Low (inverse of FilterIntoJoin)
   - Value: Medium (enables filter merge)
   - Dependencies: None

### Phase 2: Advanced Filter-Join (High Value)

4. **JoinConditionPush** ✅ Verified
   - Difficulty: High (predicate analysis)
   - Value: Very High (major performance win)
   - Dependencies: Predicate classification logic

5. **JoinPushTransitivePredicatesRule** ✅ Verified
   - Difficulty: High (equality graph analysis)
   - Value: High (predicate inference)
   - Dependencies: None

### Phase 3: Project-Join Rules (Medium Priority)

6. **JoinProjectTransposeRule**
   - Difficulty: High (expression splitting)
   - Value: High (reduces join cardinality)
   - Dependencies: None

7. **ProjectJoinRemoveRule**
   - Difficulty: Low (identity detection)
   - Value: Low (cleanup)
   - Dependencies: None

### Phase 4: Advanced Transformations (Lower Priority)

8. **JoinPushThroughJoinRule**
   - Difficulty: High (condition analysis)
   - Value: High (join reordering)
   - Dependencies: None

9. **JoinDeriveIsNotNullFilterRule**
   - Difficulty: Medium (nullability analysis)
   - Value: Medium (schema-based optimization)
   - Dependencies: None

10. **JoinPushExpressionsRule**
    - Difficulty: High (expression extraction)
    - Value: Medium (enables better join algorithms)
    - Dependencies: None

### Phase 5: Defer (Too Complex or Low Value)

- ~~JoinAssociateRule~~ - 256 combinations, unprovable
- ~~ExpandDisjunctionForJoinInputsRule~~ - Very complex
- ~~JoinConditionExpandIsNotDistinctFromRule~~ - Edge case
- ~~ProjectJoinTransposeRule~~ - Complex bidirectional

---

## Summary Statistics

- **Total Join Rules in Calcite**: 47 files
- **Implementable with Join+Filter+Project**: 16 rules
- **Verified in Java RuleScript**: 5 rules (JoinCommute, FilterIntoJoin, JoinExtractFilter, JoinConditionPush, JoinPushTransitivePredicates)
- **Unprovable but Useful**: 1 rule (JoinAssociate - too complex)
- **Recommended for Phase 1**: 3 rules
- **Recommended for Phase 2**: 2 rules

## Notes

- All rules preserve query semantics (soundness)
- Some rules are inverses (FilterIntoJoin ↔ JoinExtractFilter)
- Filter-join rules provide the highest optimization value
- Project-join rules are useful but less critical
- Join reordering (Associate, PushThrough) is complex but valuable
- Many Calcite rules have configuration options we can initially skip

---

**Generated**: 2025-10-16
**Based on**: Calcite main branch, Java RuleScript parser analysis
