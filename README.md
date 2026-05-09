# RuleScript

A Rust DSL for building database query rewrite rules with uninterpreted symbols. RuleScript wraps DataFusion's native query planning to enable declarative rule definition, pattern matching, and verification.

For details, see our [paper](https://arxiv.org/abs/2605.05536).

For the engine-agnostic Java DSL implementation (Calcite, CockroachDB, MySQL, ProxySQL backends), see [`java/`](java/README.md).

## Build

```bash
cargo build
cargo test
```

## Quick Start

### Define a Rule

```rust
crate::rule! {
    FilterMergeRule {
        schemas: {
            source: (col: T),
        },
        functions: {
            P(T) -> Bool,
            Q(T) -> Bool,
        },
        from: {
            let inner = crate::filter!(source, P(col));
            crate::filter!(inner, Q(col))
        },
        to: crate::filter!(source, P(col) && Q(col)),
    }
}
```

`P` and `Q` are uninterpreted predicates — they represent any boolean expression, so one rule definition covers infinite concrete cases.

### Apply a Rule

```rust
use rulescript::rule::{ApplicableRule, impls::FilterMergeRule};

let rule = FilterMergeRule;
let optimized_plan = rule.try_apply(&concrete_plan)?;
```

### Integrate with DataFusion

```rust
use rulescript::rule::RuleWrapper;
use datafusion::optimizer::OptimizerRule;

// wrap any RuleScript rule as a DataFusion optimizer rule
let rule: Arc<dyn OptimizerRule> = Arc::new(RuleWrapper::new(FilterMergeRule));
```

## Interactive Demo

```bash
cargo run --example optimizer
```

See [`examples/README.md`](examples/README.md) for detailed usage.

## Tests

```bash
cargo test
cargo test filter_merge        # specific rule
cargo clippy --all-targets
```

## Macros

```rust
// Define a rule
crate::rule! {
    RuleName {
        schemas:   { input: (field: Type) },
        functions: { F(Type) -> OutputType },
        from: { /* pattern to match */ },
        to:   { /* replacement */      },
    }
}

// Build plan nodes
crate::filter!(source, predicate)
crate::project!(source, [expr1, expr2])
crate::project!(source, [expr as alias])
crate::join!(left, right, Inner, condition)
crate::join!(left, right, Left, condition)
```

## Architecture

```
src/
  ast/
    opaque.rs      - Abstract types, fields, schemas
    relational.rs  - Logical plan patterns (Source, Filter, Project, Join)
    pattern.rs     - Pattern functions (ScalarPattern, AggregatePattern)
    extension.rs   - User-defined operator support
    source.rs      - Source node implementation
  matcher/
    mod.rs         - PatternMatcher trait and error types
    default.rs     - DefaultMatcher implementation
  rule/
    mod.rs         - Rule traits (RewriteRule, ApplicableRule)
    impls/         - Concrete rule implementations
  verifier/
    mod.rs         - Verifier trait
    qed.rs         - QED format serialization
  lib.rs           - Public API
examples/
  optimizer.rs                    - Interactive optimizer demo
  export_rules_to_qed.rs          - Export rules to QED format
  tpch_optimize.rs                - TPC-H query optimization
  user_defined_left_semi_join.rs  - User-defined operator example
```

See [`src/rule/impls/README.md`](src/rule/impls/README.md) for documentation on each implemented rule.

## Known Limitations

- Join rules only support INNER joins (OUTER joins require IS NOT NULL predicates)
- All abstract symbols must bind to at least one match (no optional predicates)
- Single binding per symbol per rule application

## License

Copyright 2026 The Qed Team. Licensed under the Apache License, Version 2.0.
