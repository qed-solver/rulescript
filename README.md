# RuleScript: Extensible Rule Language for Query Optimizers

## Overview

RuleScript is a domain-specific language (DSL) for expressing database query rewrite rules that can be automatically verified for correctness and used to generate implementations for extensible query optimizers.

## Key Concepts from the Paper

### Problem Statement
- Modern query optimizers use hundreds of rewrite rules (e.g., Apache Calcite has 100+, CockroachDB has 200+)
- Implementing these rules correctly is error-prone and costly
- Need for automated verification and code generation for rewrite rules

### RuleScript Approach
1. **Declarative Rule Definition**: Rules expressed as `pattern → replacement` transformations
2. **Uninterpreted Symbols**: Use abstract types and functions to represent families of concrete queries
3. **Automatic Verification**: Translate rules to QED solver for correctness proofs
4. **Code Generation**: Generate implementations for target query engines via adapters

### Core Language Features

#### Patterns
- `Plan(id, [Field(name, type)])` - Abstract query plans with typed schemas
- `Filter(predicate, source)` - Filter operations
- `Join(type, condition, left, right)` - Join operations  
- `Project(expressions, source)` - Projection operations
- `Union`, `Aggregate`, `Distinct` - Set operations

#### Uninterpreted Symbols
- **Abstract Types**: `Type { id: String }` - Variables representing any data type
- **Abstract Functions**: `Function(name, input_types, return_type)` - Variables representing any function
- **Abstract Predicates**: Functions with boolean return type (e.g., filter conditions, join predicates)

#### Rule Structure
```rust
trait RewriteRule {
    fn pattern(&self) -> Rel;      // What to match
    fn replacement(&self) -> Rel;  // How to transform
    fn name(&self) -> &str;        // Optional rule name for debugging
}
```

### Example: FilterMerge Rule
**Intent**: Merge two consecutive filters into one with AND condition
```
source.filter(inner).filter(outer) → source.filter(inner AND outer)
```

**Using Abstract Predicates**:
- `inner` and `outer` are uninterpreted boolean functions
- Verification proves correctness for ALL possible instantiations
- Code generation handles pattern matching and transformation

## Current Implementation Status

### ✅ Implemented (Rust)
- Core AST types: `Type`, `Field`, `Schema`, `Function`, `Rel`, `Scalar`  
- DataFusion integration via custom `Source` nodes using `UserDefinedLogicalNodeCore`
- `RewriteRule` trait interface with instance methods
- Abstract type system with global unique ID generation
- Working FilterMerge example demonstrating complete rule construction
- Abstract functions that integrate with DataFusion's UDF system for pattern matching

### ✅ Architecture Principles

**Minimal API Surface**: RuleScript exposes DataFusion's native APIs through thin wrappers with public fields. Users construct rules using DataFusion's `LogicalPlanBuilder` and existing expression constructors.

**Working FilterMerge Example** (see `examples/filter_merge.rs`):
```rust
use datafusion::logical_expr::{BinaryExpr, Operator, builder::LogicalPlanBuilder};
use rulescript::{Field, Function, Rel, RewriteRule, Schema, Type};

impl RewriteRule for FilterMergeRule {
    fn pattern(&self) -> Rel {
        // Create abstract schema and source
        let schema = Schema { 
            fields: vec![Field {
                name: "col".to_string(),
                data_type: Type::Generic { id: "T".to_string() },
                nullable: false,
            }]
        };
        let source_rel = Rel::source("table".to_string(), schema);
        
        // Create abstract predicates P(col) and Q(col)
        let inner_predicate = Function::boolean_predicate("P".to_string(), 1)
            .call(vec![col("col")]);
        let outer_predicate = Function::boolean_predicate("Q".to_string(), 1)
            .call(vec![col("col")]);
        
        // Pattern: source.filter(P).filter(Q)
        let inner_filter = LogicalPlanBuilder::from(source_rel.plan)
            .filter(inner_predicate).unwrap().build().unwrap();
        let outer_filter = LogicalPlanBuilder::from(inner_filter)
            .filter(outer_predicate).unwrap().build().unwrap();
            
        Rel { plan: outer_filter }
    }
    
    fn replacement(&self) -> Rel {
        // Same setup...
        // Replacement: source.filter(P AND Q) 
        let combined = Expr::BinaryExpr(BinaryExpr {
            left: Box::new(inner_predicate),
            op: Operator::And, 
            right: Box::new(outer_predicate),
        });
        // Build merged filter plan...
    }
}
```

**Key Features**:
- Abstract functions (`P`, `Q`) represent uninterpreted predicates
- Custom `Source` nodes integrate seamlessly with DataFusion's optimizer
- Rule construction uses familiar DataFusion patterns
- Ready for verification and code generation pipelines

### 🎯 Ultimate Goals

1. **Rule Expression**: DSL for writing rewrite rules with abstract symbols
2. **Verification**: Integrate with automated theorem provers (QED, CVC5, Z3)
3. **Code Generation**: Adapters to generate DataFusion/Calcite implementations
4. **Rule Interpreter**: Generic rule engine that applies rules to concrete queries

## Architecture

```
RuleScript Rule Definition
         ↓
┌─────────────────┬─────────────────┐
│   Verification  │  Code Generation │
│   (QED Solver)  │   (Adapters)     │
└─────────────────┴─────────────────┘
         ↓                 ↓
   Correctness Proof   Implementation
```

## Next Steps

1. **Immediate**: Add more rule examples (ProjectionPushdown, JoinReordering)
2. **Short-term**: Implement rule verification pipeline using SMT solvers  
3. **Medium-term**: DataFusion adapter for code generation
4. **Long-term**: Generic rule interpreter and additional target engines

## Running the Example

```bash
# View the working FilterMerge rule implementation
cargo run --example filter_merge

# Output shows pattern vs replacement:
# Pattern: Filter: Q(col) Filter: P(col) Source: table [fields: 1] 
# Replacement: Filter: P(col) AND Q(col) Source: table [fields: 1]
```

## Related Work
- **QED**: Query equivalence verification solver
- **Apache Calcite**: Extensible query optimizer framework
- **CockroachDB**: Production system with rule-based optimizer
- **HoTTSQL/Cosette**: Earlier DSLs for query verification