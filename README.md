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
    fn pattern() -> Rel;      // What to match
    fn replacement() -> Rel;  // How to transform
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
- DataFusion integration via custom `Source` nodes
- `RewriteRule` trait interface
- Abstract type system with unique IDs

### 🚧 Architecture Principles

**Minimal API Surface**: RuleScript exposes DataFusion's native APIs through thin wrappers with public fields. Users can directly construct rules using DataFusion's existing constructors (e.g., `LogicalPlan::Filter`, `Expr::BinaryExpr`) without needing custom builders.

**Example FilterMerge Construction**:
```rust
use datafusion::logical_expr::{LogicalPlan, Expr, Filter};
use rulescript::{Rel, RewriteRule};

impl RewriteRule for FilterMergeRule {
    fn pattern() -> Rel {
        Rel {
            plan: LogicalPlan::Filter(Filter {
                predicate: outer_predicate, // Expr constructed directly
                input: Arc::new(LogicalPlan::Filter(Filter {
                    predicate: inner_predicate, 
                    input: source_plan,
                }))
            })
        }
    }
    
    fn replacement() -> Rel {
        Rel {
            plan: LogicalPlan::Filter(Filter {
                predicate: Expr::BinaryExpr(BinaryExpr {
                    left: Box::new(inner_predicate),
                    op: Operator::And,
                    right: Box::new(outer_predicate),
                }),
                input: source_plan,
            })
        }
    }
}
```

**Key Insight**: Since all wrapper structs have public fields and DataFusion provides rich constructors, users can compose rules directly without additional convenience APIs. Focus remains on verification and code generation pipelines.

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

1. **Immediate**: Add missing builder methods for FilterMerge rule
2. **Short-term**: Implement rule verification pipeline
3. **Medium-term**: DataFusion adapter for code generation
4. **Long-term**: Generic rule interpreter and additional target engines

## Related Work
- **QED**: Query equivalence verification solver
- **Apache Calcite**: Extensible query optimizer framework
- **CockroachDB**: Production system with rule-based optimizer
- **HoTTSQL/Cosette**: Earlier DSLs for query verification