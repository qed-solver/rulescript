# RuleScript

A Rust DSL for building database query rewrite rules with uninterpreted symbols. RuleScript provides a minimal, pragmatic API that wraps DataFusion's native query planning while enabling rule verification and code generation.

## What It Does

RuleScript lets you express query optimizer rewrite rules using abstract patterns with uninterpreted symbols:

```rust
// Pattern: source.filter(P).filter(Q) → source.filter(P AND Q)
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

The `P` and `Q` are uninterpreted predicates - they can represent ANY boolean expression. This means one rule definition covers infinite concrete cases.

## Current State

### Implemented Rules (11 total)

**Filter Rules:**
- FilterMergeRule - Merge consecutive filters
- FilterProjectTransposeRule - Push filter below projection

**Project Rules:**
- ProjectMergeRule - Merge consecutive projections
- ProjectRemoveRule - Remove identity projections (works on any input including joins)

**Join Rules:**
- JoinCommuteRule - Swap join inputs
- JoinConditionPushRule - Push join predicates down as filters
- JoinExtractFilterRule - Extract join condition as filter above join
- FilterIntoJoinRule - Merge filter into join condition
- JoinLeftProjectTransposeRule - Pull projection from left join input up
- JoinRightProjectTransposeRule - Pull projection from right join input up
- JoinAssociateRule - Restructure nested joins using associativity

All rules have comprehensive tests (44 tests total, all passing).

See `src/rule/impls/README.md` for detailed rule documentation.

### Core Features

- **Pattern Matching**: Full support for Filter, Project, and Join plans
- **Predicate Decomposition**: Automatic splitting of conjunctive predicates based on column dependencies
- **Function Composition**: Support for nested function applications (e.g., `f(g(x))`)
- **Alias Handling**: Transparent matching through alias wrappers
- **Column Abstraction**: Smart column pattern matching that works with field partitions
- **DataFusion Integration**: RuleWrapper adapter for seamless optimizer integration

### Architecture

```
src/
  ast/
    opaque.rs      - Abstract types, fields, schemas
    relational.rs  - Logical plan patterns (Source, Filter, Project, Join)
    pattern.rs     - Pattern functions (ScalarPattern, AggregatePattern)
  matcher/
    mod.rs         - PatternMatcher trait and error types
    default.rs     - DefaultMatcher with full pattern matching logic
  rule/
    mod.rs         - Rule traits (RewriteRule, ApplicableRule)
    test.rs        - Test utilities (table helpers)
    impls/         - Concrete rule implementations
  lib.rs           - Public API exports
examples/
  optimizer_repl/  - Interactive demo with all rules
```

## Quick Start

### Define a Rule

```rust
crate::rule! {
    MyRule {
        schemas: {
            source: (x: T),
        },
        functions: {
            P(T) -> Bool,
        },
        from: crate::filter!(source, P(x)),
        to: source,  // Remove the filter
    }
}
```

### Apply a Rule

```rust
use rulescript::rule::{ApplicableRule, impls::FilterMergeRule};

let rule = FilterMergeRule;
let optimized_plan = rule.try_apply(&concrete_plan)?;
```

### Integrate with DataFusion

```rust
use rulescript::rule::RuleWrapper;
use datafusion::optimizer::Optimizer;

let optimizer_rule = RuleWrapper::new(FilterMergeRule);
optimizer.add_rule(Arc::new(optimizer_rule));
```

## Run Interactive Demo

```bash
cargo run --example optimizer_repl
```

Interactive demonstration with all 11 implemented rules:
- Choose which rules to apply
- See before/after query plans
- Real SQL parsing with DataFusion

See `examples/README.md` for detailed usage.

## Run Tests

```bash
# All tests
cargo test

# Specific rule
cargo test filter_merge

# With output
cargo test -- --nocapture

# Clippy checks
cargo clippy --all-targets
```

## Macro Reference

### rule! - Define Complete Rules

```rust
crate::rule! {
    RuleName {
        schemas: {
            input_name: (field: Type),
            other_input: (x: T1, y: T2),
        },
        functions: {
            FuncName(InputType) -> OutputType,
            Predicate(T1, T2) -> Bool,
        },
        from: { /* pattern to match */ },
        to: { /* replacement pattern */ },
    }
}
```

### Plan Construction Macros

```rust
// Filter
crate::filter!(source, predicate)

// Project
crate::project!(source, [expr1, expr2])
crate::project!(source, [expr as alias])

// Join
crate::join!(left, right, Inner, condition)
crate::join!(left, right, Left, condition)
```

## Theoretical Foundation

Based on the paper "RuleScript: A DSL for Query Optimizer Rules" which addresses the challenge of correctly implementing hundreds of rewrite rules in modern optimizers.

**Key Concepts:**
- **Uninterpreted Symbols**: Abstract types/functions represent families of concrete queries
- **Pattern Matching**: Declarative patterns with automatic instantiation
- **Verification**: Rules can be verified via QED solver (future integration)

**Why This Approach:**

Modern query optimizers suffer from:
1. Error-prone manual implementation (100-200+ rules per optimizer)
2. Difficult to verify correctness
3. Redundant code across similar rules

RuleScript solves this by:
1. One rule definition → many concrete applications
2. Automated verification possible
3. Declarative patterns reduce implementation complexity

## Key Design Decisions

**Pattern Matching:**
- Column patterns match based on field partitions (one pattern can match multiple columns)
- Predicates decompose automatically based on column dependencies
- Function composition works through context mapping

**Implementation:**
- Minimal abstraction over DataFusion's native types
- No async/tokio in tests (fast, simple tests)
- Smart defaults (all types map to Binary for uniformity)
- Functions are UDFs that error on execution (pattern-only)

**Rule Application:**
- DefaultMatcher manages three binding types: fields, functions, sources
- Context-preserving instantiation (bindings stay in their context)
- Recursive plan transformation with captured bindings

## External Resources

The project references two external directories not tracked in git:

- `parser/` - Java implementation with QED-verified rules
- `calcite/` - Apache Calcite source for rule reference

See `PARSER_AND_CALCITE_NOTES.txt` for details on these directories.

## Future Work

**Near-term:**
- Support for Aggregate and Union in patterns
- More complex join rules (with 4-predicate decomposition)
- Rule families with meta-variables

**Long-term:**
- QED export for verification
- SMT solver integration
- Code generation adapters for different engines
- Performance optimizations for matching

## Known Limitations

**Current Implementation:**
- Join rules only support INNER joins (OUTER joins require IS NOT NULL predicates)
- Some complex expression types not yet handled (SIMILAR TO with escape)
- No optimization for pattern matching efficiency

**Design Constraints:**
- All abstract symbols must bind to at least one match (no optional predicates)
- Single binding per symbol per rule application
- Strict column validation (all references must exist in context)

## Dependencies

- datafusion - Query planning framework
- thiserror - Error handling macros

## Status

Active development. Core pattern matching complete. API stabilizing.

**Test Status**: 44 tests passing (11 doc tests + 33 unit tests)

The project emphasizes rapid prototyping over completeness. Pattern matching and instantiation are fully implemented with 11 working rules demonstrating the approach works with real DataFusion plans.
