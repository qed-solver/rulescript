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

### Working
- **Core AST** wrapping DataFusion's `LogicalPlan` and `Expr`
- **Abstract types** mapping to Binary in DataFusion (uniform representation)
- **Abstract functions** as UDFs for pattern matching (not execution)
- **Custom `Source` nodes** via DataFusion's `UserDefinedLogicalNodeCore`
- **FilterMerge example** demonstrating complete rule construction

### Architecture
```
src/
  ast/
    opaque.rs    - Abstract types/fields/schemas with ID generation
    relational.rs - Source pattern integrating with DataFusion
    scalar.rs    - Abstract functions as DataFusion UDFs
  rule.rs        - RewriteRule trait
```

### Key Design Decisions
- Using DataFusion's native types where possible
- Custom nodes only for terminal patterns (`Source`)
- All abstract types map to Binary for uniformity
- Functions are UDFs that error on execution (pattern-only)
- Public fields for direct manipulation

## Example Usage

```rust
use datafusion::logical_expr::{BinaryExpr, Operator, builder::LogicalPlanBuilder};
use rulescript::{Field, Function, Rel, RewriteRule, Schema, Type};

impl RewriteRule for FilterMergeRule {
    fn pattern(&self) -> Rel {
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
        LogicalPlanBuilder::from(source.plan)
            .filter(P.call(vec![col("col")]))
            .unwrap()
            .build()
            .unwrap()
            .filter(Q.call(vec![col("col")]))
            .unwrap()
            .build()
            .unwrap()
    }
    
    fn replacement(&self) -> Rel {
        // Same setup...
        // Replacement: source.filter(P AND Q)
        let combined = Expr::BinaryExpr(BinaryExpr {
            left: Box::new(P.call(vec![col("col")])),
            op: Operator::And,
            right: Box::new(Q.call(vec![col("col")])),
        });
        
        LogicalPlanBuilder::from(source.plan)
            .filter(combined)
            .unwrap()
            .build()
            .unwrap()
    }
}
```

## Run Example

```bash
cargo run --example filter_merge
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
- [ ] More rule examples (ProjectionPushdown, JoinAssociate)
- [ ] Basic pattern matching engine

**Short-term**
- [ ] SMT solver integration for verification
- [ ] Rule enumeration with meta-variables

**Long-term**
- [ ] DataFusion optimizer integration
- [ ] Code generation adapters

## Dependencies

- `datafusion = "*"` - Query planning framework
- `smtlib = "*"` - Future solver integration

## Status

Active development. API unstable. Not production ready.

The project emphasizes rapid prototyping over completeness. We build the minimum required to validate ideas, then iterate based on real usage.