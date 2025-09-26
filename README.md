# RuleScript

A Rust DSL for building database query rewrite rules with uninterpreted symbols. RuleScript provides a minimal, pragmatic API that wraps DataFusion's native query planning while enabling rule verification and code generation.

## Engineering Philosophy

**Rapid Development First**: We prioritize minimal viable implementations that work. No unit tests until final stages. No boilerplate code. Direct exposure of DataFusion's APIs with thin wrappers.

## What It Does

RuleScript lets you express query optimizer rewrite rules like "merge two filters into one" using abstract patterns:

```rust
// Pattern: source.filter(P).filter(Q) → source.filter(P AND Q)
let pattern = source.filter(P).filter(Q);
let replacement = source.filter(P.and(Q));
```

The `P` and `Q` are uninterpreted predicates - they can represent ANY boolean expression. This means one rule definition covers infinite concrete cases.

## Current State

### Completed ✅
- **Core AST** wrapping DataFusion's `LogicalPlan` and `Expr`
- **Abstract types** mapping to Binary in DataFusion (uniform representation)
- **Abstract functions** as UDFs for pattern matching (not execution)
- **Custom `Source` nodes** via DataFusion's `UserDefinedLogicalNodeCore`
- **Helper methods** on `Rel` for ergonomic plan construction (`filter`, `project`, `join`, etc.)
- **Complete rule abstractions** with `RewriteRule`, `PatternMatcher`, and `ApplicableRule` traits
- **DefaultMatcher** with full pattern matching and instantiation:
  - Expression matching with AND/OR commutativity support
  - Column validation for field partitions
  - Abstract function binding to concrete expressions
  - Source pattern matching to logical plans
  - Context-preserving instantiation principle
  - Function composition support in templates (e.g., `f(g(x))`)
  - Alias handling in both pattern and concrete expressions
- **Template instantiation logic**:
  - Recursive plan transformation using captured bindings
  - Column replacement with context mapping for function composition
  - Proper expression ordering preservation
  - Support for most Expr types (Column, BinaryExpr, ScalarFunction, literals, etc.)
- **DataFusion optimizer integration**:
  - `RuleWrapper<R, M>` adapter for OptimizerRule trait
  - Generic over matcher type with DefaultMatcher as default
  - Recursive rule application with `transform_down`
- **Optimized codebase** with minimal cloning overhead
- **Clean error types** with descriptive messages and concrete values
- **Concrete rule implementations** in `src/rule/impls/`:
  - FilterMergeRule ✅ - Fully working with tests
  - ProjectRemoveRule ✅ - Fully working with tests  
  - ProjectMergeRule ⚠️ - Pattern compiles, needs abstract functions for full matching
  - FilterProjectTransposeRule ⚠️ - Pattern compiles, needs abstract functions
  - ProjectFilterTransposeRule ⚠️ - Pattern compiles, needs abstract functions
- **Smart column pattern matching**:
  - Column patterns in projections match ordered sequences
  - Column patterns in function arguments match any from partition
  - Enables proper identity projection detection
- **Minimal test infrastructure**:
  - No async/tokio dependencies in tests
  - Direct LogicalPlan construction without SessionContext
  - Tests run in milliseconds

### In Progress 🚧
- **Full rule testing** - Need to create plans with abstract functions for complete pattern matching
- **Additional plan types** - Support for Join, Union, Aggregate in matcher

### Architecture
```
src/
  ast/
    opaque.rs     - Abstract types/fields/schemas with ID generation
    relational.rs - Source pattern & helper methods for plan construction
    scalar.rs     - Abstract functions as DataFusion UDFs
  matcher/
    mod.rs        - PatternMatcher trait, error types, and utilities
    default.rs    - DefaultMatcher implementation with full matching logic
  rule.rs         - Rule traits (RewriteRule, ApplicableRule)
  lib.rs          - Public API exports
```

### Key Design Decisions
- Using DataFusion's native types where possible
- Direct construction of LogicalPlan nodes (avoiding builder overhead)
- All abstract types map to Binary for uniformity
- Functions are UDFs that error on execution (pattern-only)
- Unified `DefaultMatcher` manages three binding types:
  - `fields`: Abstract field → Ordered list of concrete columns
  - `functions`: Abstract function → List of concrete expressions
  - `sources`: Source name → Original LogicalPlan
- Context-preserving principle: expressions bound in one context stay in that context
- Column patterns match differently based on context:
  - Direct projection expressions: match full ordered sequence
  - Function arguments: match any column from partition
- Efficient pattern partitioning with descriptive error reporting

## Example Usage

```rust
use datafusion::prelude::col;
use rulescript::{Field, Function, Rel, RewriteRule, Schema, Type};

struct FilterMergeRule;

impl RewriteRule for FilterMergeRule {
    fn from(&self) -> Rel {
        let schema = Schema { 
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic { id: "T".to_string() },
                nullable: false,
            }]
        };
        
        let source = Rel::source("table".to_string(), schema);
        let P = Function::boolean_predicate("P".to_string(), 1);
        let Q = Function::boolean_predicate("Q".to_string(), 1);
        
        // Pattern: source.filter(P).filter(Q)
        source
            .filter(P.call(vec![col("col")])).unwrap()
            .filter(Q.call(vec![col("col")])).unwrap()
    }
    
    fn to(&self) -> Rel {
        // Same setup...
        let source = Rel::source("table".to_string(), schema);
        let P = Function::boolean_predicate("P".to_string(), 1);
        let Q = Function::boolean_predicate("Q".to_string(), 1);
        
        // Replacement: source.filter(P AND Q)
        source.filter(P.and(vec![col("col")], Q.call(vec![col("col")]))).unwrap()
    }
}

// Apply the rule  
use rulescript::{ApplicableRule, DefaultMatcher, RuleWrapper};
use datafusion::optimizer::Optimizer;

impl ApplicableRule<DefaultMatcher> for FilterMergeRule {}

// Use directly
let rule = FilterMergeRule;
let new_plan = rule.try_apply(&concrete_plan)?;

// Or integrate with DataFusion's optimizer
let optimizer_rule = RuleWrapper::new(FilterMergeRule);
optimizer.add_rule(Arc::new(optimizer_rule));
```

## Run Tests

```bash
# Build the project
cargo build

# Run all tests
cargo test

# Run with clippy checks
cargo clippy --all-targets
```

## Theoretical Foundation

Based on the paper "Extensible Rule Language for Query Optimizers" (VLDB 2025), RuleScript addresses the challenge of correctly implementing hundreds of rewrite rules in modern optimizers:

- **Uninterpreted Symbols**: Abstract types/functions represent families of concrete queries
- **Verification Pipeline**: Rules can be verified via QED solver (future integration)
- **Code Generation**: Generate implementations for different engines via adapters (future)

The key insight: Express rules with abstract symbols, verify once, apply to infinite concrete cases.

## Why This Approach

Modern query optimizers (Calcite: 100+ rules, CockroachDB: 200+ rules) suffer from:
1. Error-prone manual implementation
2. Difficult to verify correctness
3. Redundant code across similar rules

RuleScript solves this by:
1. One rule definition → many concrete applications
2. Automated verification possible (QED integration planned)
3. Code generation from verified rules (adapter system planned)

## Next Steps

**Immediate**
- [x] ~~Implement `DefaultMatcher` pattern matching logic~~ ✅ Complete
- [x] ~~Implement `instantiate` method for template transformation~~ ✅ Complete
- [x] ~~DataFusion optimizer integration~~ ✅ Complete via RuleWrapper
- [ ] Create concrete rule examples with real DataFusion plans
- [ ] Test function composition with chained projections
- [ ] More rule examples (ProjectionPushdown, JoinAssociate)

**Short-term**
- [ ] Support additional plan types (Join, Union, Aggregate) in matcher
- [ ] QED export for verification
- [ ] Rule enumeration with meta-variables
- [ ] Comprehensive testing suite

**Long-term**
- [ ] SMT solver integration for verification
- [ ] Code generation adapters for different engines
- [ ] Performance optimizations for matching
- [ ] Support for more complex expression types (windows, subqueries)

## Dependencies

- `datafusion = "*"` - Query planning framework
- `smtlib = "*"` - Future solver integration
- `thiserror = "*"` - Error handling macros

## Known Limitations

### Current Implementation
- **Plan Types**: Only Filter and Projection are fully supported in matcher
- **Expression Types**: Some complex expressions not yet handled in instantiation (SIMILAR TO, LIKE with escape chars)
- **Pattern Matching**: Abstract functions can't match regular binary expressions (need plans with abstract functions)
- **Performance**: No optimizations for pattern matching efficiency

### Design Decisions
- **Function Composition**: Requires properly chained projections, not arbitrary nesting
- **Strict Validation**: All column references must exist in context (no partial matches)
- **Single Binding**: Abstract symbols can only bind to one concrete value per rule application
- **Testing Philosophy**: Minimal tests without heavy dependencies (no tokio, no CSV files)

## Status

Active development. API unstable. Not production ready.

The project emphasizes rapid prototyping over completeness. We build the minimum required to validate ideas, then iterate based on real usage.