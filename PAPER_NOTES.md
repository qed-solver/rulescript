# RuleScript Paper Notes

## Core Concepts

### Problem Statement
- Modern optimizers have 100-200+ rewrite rules
- Each rule ~200 lines of code (Calcite) or ~30 lines DSL (CockroachDB)
- Correctness is critical but hard to ensure
- Failures spotted in production, converted to test cases

### RuleScript Solution
1. **Declarative Rules**: `pattern → replacement` transformations
2. **Uninterpreted Symbols**: Variables representing families of concrete queries
3. **Verification**: Translate to QED solver for correctness proofs
4. **Code Generation**: Generate implementations via adapters

## Language Design

### Core Syntax (Section 3.1)

```
<Rule>   := Rule(<Patn>, <Patn>, <FOPred>)
<Patn>   := Plan(<Id>, [<Field>])
          | Empty([<Field>])
          | Filter(<Pred>, <Patn>)
          | Project([<Call>], <Patn>)
          | Join(<J_ty>, <Pred>, <Patn>, <Patn>)
          | Union(<Patn>, <Patn>)
          | Aggregate(<Call>, <Call>, <Patn>)
          | Distinct(<Patn>)
<FOPred> := FORALL [<Id>] <Pred> | EXISTS [<Id>] <Pred>
<Field>  := Field(<Id>, <Type>, <Stat>)
<Pred>   := <Call> | TRUE | FALSE | NOT <Pred>
          | <Pred> AND <Pred> | <Pred> OR <Pred>
          | <Call> IS NOT NULL | <Call> IS NULL
          | <Call> = <Call> | <Call> != <Call>
<Call>   := <Id> | <Lit> | <Op>([<Call>])
<J_ty>   := "Inner" | "Left" | "Right" | "Outer" | "Semi" | "Anti"
<Op>     := Op(<Id>, <Type>, <Type>)
```

### Uninterpreted Symbols
1. **Types**: `Type { id: String }` - Can be ANY concrete type
2. **Functions**: `Op(name, input_type, output_type)` - Can be ANY function with matching signature
3. **Predicates**: Functions returning boolean
4. **Plans**: `Plan(id, fields)` - Can match ANY query plan with compatible schema

### Key Semantics (Section 3.2)

#### Plan Pattern
- Represents ANY query plan where:
  - There exists an instantiation of uninterpreted types making field types match
  - The instantiation preserves properties (nullable, unique, etc.)
- In match pattern: Verifies if query plan is valid interpretation
- In transform pattern: Refers to actual matched query plan

#### Filter/Join Patterns
- Predicate can be ANY boolean expression
- Matching finds instantiation making pattern predicate ≡ concrete predicate
- Example: `P0(x) AND P1(y)` can match `x+y=3 AND x-y=1` with P0(x)=`x=2`, P1(y)=`y=1`

#### Project Pattern
- Contains ordered list of `<Call>`s
- Matches if projection function can be segmented into interpretations of the calls
- Transform uses found interpretations to build new projection

## Verification (Section 5)

### QED Solver Integration
- Translates patterns to semi-ring expressions
- Normalizes to finite summation of terms
- Uses SMT solver to check term equivalence
- Supports bag semantics (multiplicity matters, order doesn't)

### Query Plan Formalism (Table in Section 5.1)
```
Q, Q₀, Q₁     - Query plans outputting tuples
P, P₀, P₁     - Predicates (tuple → Bool)
f, f₀, f₁     - Functions (tuple → tuple)
α             - Aggregations (bag of tuples → tuple)
Table(R: S)   - Table R with schema S
Filter(P, Q)  - Filter Q with P
Project(f, Q) - Map Q with f
Join(τ, P, Q₀, Q₁) - τ-Join Q₀ and Q₁ on P
Union(Q₀, Q₁) - Union Q₀ and Q₁
Aggregate(α, f, Q) - Aggregate Q with α grouped by f
Distinct(Q)   - Deduplicate Q
```

## Extension Mechanism (Section 4)

### Alias Interface
- Define custom patterns with `semantics()` method
- Returns equivalent pattern using core syntax
- Examples:
  - `Calculate(pred, proj, source)` → `Project(proj, Filter(pred, source))`
  - `IndexJoin(pairs, left, right)` → `Join(Inner, pairwise_equals, left, right)`

### Meta-Variables and Rule Families
- Placeholders in patterns with temporarily unknown semantics
- Rule family = set of rules from different assignments
- Example: JoinAssociate with different join types
  - 256 possible combinations of 4 join types
  - Only 16 are provably correct (listed in paper)

## Implementation Architecture

### Rule Interpretation Flow
1. **Match Phase**: 
   - Recursively verify query plan against pattern
   - Find instantiations for uninterpreted symbols
   - Build context with matched information

2. **Transform Phase**:
   - Use instantiations from match phase
   - Recursively build new query plan from transform pattern
   - Apply captured instantiations

### Code Generation Pipeline (Section 6)
```
Initialize Context → Match Phase → Transform Phase → Extract Implementation
                          ↓              ↓
                    Match Hooks    Transform Hooks
```

### Adapter Design Requirements
- Match hooks for each pattern type
- Transform hooks for each pattern type
- Context management for captured information
- Generated code can be stricter than rule (soundness over completeness)

## Critical Examples

### FilterMerge Rule
```
Pattern:     Filter(Q(col), Filter(P(col), source))
Replacement: Filter(P(col) AND Q(col), source)
```
- P and Q are uninterpreted predicates
- Works for ANY predicates, not just specific ones

### JoinAssociate Rule (Motivating Example)
```
Pattern:     Join(Outer, F1, Join(Outer, F0, Q0, Q1), Q2)
Replacement: Join(Outer, F0, Q0, Join(Outer, F1, Q1, Q2))
Where:       F0 = P0(x,y) AND y IS NOT NULL
             F1 = P1(y,z) AND y IS NOT NULL
```
- Restricted predicates prevent incorrect rewrites
- Different from Inner Join case (simpler predicates)
- Paper proves 16 valid join type combinations

### JoinPredicate Rule (Push Down)
```
Pattern:     Join(Inner, P0(x,y) AND P1(x) AND P2(y), Q0, Q1)
Replacement: Join(Inner, P0(x,y), Filter(P1(x), Q0), Filter(P2(y), Q1))
```
- Pushes predicates to inputs when possible
- P1 depends only on Q0, P2 only on Q1

## Implementation Notes for Our System

### Current Status vs Paper
- We have: Core AST, abstract types/functions, Source pattern
- Paper has: Full verification pipeline, code generation, meta-variables
- Gap: Verification export, rule interpreter, pattern matching engine

### For Rule Interpreter (Our Goal)
Need to implement:
1. **Pattern Matching Engine**:
   - Recursively match concrete plan against pattern
   - Track instantiations of uninterpreted symbols
   - Verify instantiations are consistent

2. **Instantiation Tracking**:
   - Map from symbol names to concrete expressions/types
   - Ensure same symbol → same instantiation throughout

3. **Transform Engine**:
   - Apply captured instantiations to transform pattern
   - Recursively build new plan

4. **Export for Verification**:
   - Serialize patterns to QED format
   - Include uninterpreted symbol declarations
   - Add constraints if any

### Key Insights for Implementation

1. **Uninterpreted Symbol Consistency**: Same name = same instantiation throughout rule

2. **Pattern Matching is Bidirectional**: 
   - Match: Concrete → Pattern (find instantiations)
   - Transform: Pattern → Concrete (apply instantiations)

3. **Soundness over Completeness**: OK to miss some valid rewrites, but never do invalid ones

4. **Context is Critical**: Must track all matched information for transform phase

5. **Recursive Structure**: Both matching and transformation follow AST structure

## Limitations to Consider

1. **Bag Semantics Only**: No support for ORDER BY, LIMIT (list semantics)
2. **QED Limitations**: Some rules can't be verified yet
3. **Performance**: Runtime pattern matching has overhead
4. **Expressiveness**: Can't express all possible rules (e.g., rules depending on data statistics)

## Future Directions Mentioned

1. **List Semantics Support**: Need different solver than QED
2. **Statistics-Aware Rules**: Rules that consider selectivity, cardinality
3. **Incremental Matching**: Reuse matching work across rules
4. **Synthesis Integration**: Use SyGuS to find optimal instantiations