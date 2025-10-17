# RuleScript Macro System

This document describes the declarative macro system for defining rewrite rules.

## Overview

We have implemented a hierarchical macro system that makes rule definition concise and readable:

1. **`schema!`** - Define schemas with abstract types
2. **`functions!`** - Declare abstract functions
3. **`filter!`, `project!`, `join!`** - Build logical plan patterns
4. **`rule!`** - Define complete rewrite rules

## Macro Reference

### `schema!` - Schema Definition

Creates a `Schema` with one or more fields.

**Syntax:**
```rust
schema!(field: Type)                        // Single field (nullable)
schema!(field: Type nullable)               // Explicitly nullable
schema!(field: Type not_null)               // Non-nullable
schema!(f1: T1, f2: T2, f3: T3)            // Multiple fields
schema!(f1: T1, f2: T2 not_null, f3: T3)   // Mixed nullability
```

**Examples:**
```rust
let s1 = schema!(col: T);                          // One nullable field
let s2 = schema!(x: T not_null);                   // One non-nullable field
let s3 = schema!(x: T, y: U, z: V);               // Three nullable fields
let s4 = schema!(x: T, y: U not_null, z: V);      // Mixed nullability
```

### `functions!` - Function Declaration

Creates local variable bindings for abstract functions.

**Syntax:**
```rust
functions! {
    name(ArgType1, ArgType2, ...) -> ReturnType,
    ...
}
```

Use `Bool` for predicates, or any identifier for generic types.

**Examples:**
```rust
functions! {
    P(T) -> Bool,              // Unary predicate
    Q(T, U) -> Bool,           // Binary predicate
    f(T) -> U,                 // Transform function
    g(U, V) -> W,              // Binary function
}

// After expansion, P, Q, f, and g are available as variables
let expr = P.call(vec![col("x")]);
```

### `filter!` - Filter Operator

Creates a Filter logical plan node.

**Syntax:**
```rust
filter!(predicate, input)
filter!(P(x) && Q(y), input)
filter!(P(x) || Q(y), input)
```

**Examples:**
```rust
filter!(P(col), source)                    // Simple filter
filter!(P(col) && Q(col), source)          // AND filter
filter!(P(x) || Q(y), source)              // OR filter
```

**Note:** ✅ Supports nested function calls like `P(f(x))`!

### `project!` - Projection Operator

Creates a Projection logical plan node.

**Syntax:**
```rust
project!([expr1, expr2, ...], input)
```

**Examples:**
```rust
project!([col], source)                    // Single column
project!([f(x)], source)                   // Function call
project!([f(x) as y], source)              // With alias
project!([f(x), g(y) as z], source)        // Multiple expressions
```

**Note:** ✅ Supports nested function calls like `g(f(x))`!

### `join!` - Join Operator

Creates a Join logical plan node.

**Syntax:**
```rust
join!(JoinType, condition, left, right)
```

**Examples:**
```rust
join!(Inner, pred(l, r), left, right)
join!(Left, pred(l, r) && pred2(l, r), left, right)
```

## Supported Operators

Currently, RuleScript supports three core operators:
- **`filter!`** - Filter operator with predicate patterns
- **`project!`** - Projection operator with expression lists
- **`join!`** - Join operator with join type and conditions

Additional operators (`union!`, `aggregate!`, `distinct!`, `limit!`) will be added as needed.

### `rule!` - Complete Rule Definition

The main macro that generates a full rewrite rule implementation.

**Syntax:**
```rust
rule! {
    RuleName {
        schemas: {
            name: (field1: Type1, field2: Type2, ...),
            ...
        },
        functions: {
            func1(Args) -> RetType,
            ...
        },
        from: pattern_expression,
        to: replacement_expression,
    }
}
```

**Features:**
- Automatically implements `RewriteRule` and `ApplicableRule<DefaultMatcher>`
- Schema names become variables in `from:` and `to:` expressions
- Function names become variables after `functions:` block
- Supports both single expressions and blocks with `{...}` for complex patterns

## Complete Examples

### Simple Rule: FilterMerge

```rust
rule! {
    FilterMergeRule {
        schemas: {
            source: (col: T),
        },
        functions: {
            P(T) -> Bool,
            Q(T) -> Bool,
        },
        from: filter!(P(col), filter!(Q(col), source)),
        to: filter!(P(col) && Q(col), source),
    }
}
```

Compares to hand-written (~100 lines):
- **Before:** 100+ lines with repetitive struct construction
- **After:** 11 lines, purely declarative

### Rule with Blocks: ProjectMerge

```rust
rule! {
    ProjectMergeRule {
        schemas: {
            source: (col: Tc),
        },
        functions: {
            f(Tc) -> Tf,
            g(Tf) -> Tg,
        },
        from: {
            let inner = project!([f(col) as f_output], source);
            project!([g(f_output)], inner)
        },
        to: {
            // For nested calls, use raw Rust syntax
            source.project(vec![g.call(vec![f.call(vec![col("col")])])]).unwrap()
        },
    }
}
```

### Join Rule (Future)

```rust
rule! {
    FilterIntoJoin {
        schemas: {
            left: (col_l: TL),
            right: (col_r: TR),
        },
        functions: {
            join_cond(TL, TR) -> Bool,
            filter_pred(TL, TR) -> Bool,
        },
        from: filter!(filter_pred(col_l, col_r), join!(Inner, join_cond(col_l, col_r), left, right)),
        to: join!(Inner, join_cond(col_l, col_r) && filter_pred(col_l, col_r), left, right),
    }
}
```

## Current Limitations

1. **✅ Nested function calls ARE supported**: Expressions like `P(f(x))` and `g(f(x))` work in macro syntax!
   ```rust
   filter!(P(f(x)), source)     // ✅ Works!
   project!([g(f(x))], source)  // ✅ Works!
   ```

2. **Limited complex predicate patterns**: The `&&` and `||` operators work for simple cases but not deeply nested predicates like `P(f(x)) && Q(g(y))`.

3. **Block syntax for very complex cases**: When patterns get very complex, you can still use block syntax `{ ... }` with intermediate variables.

## Implementation Details

### Macro Locations

- `schema!` - Defined in `src/ast/opaque.rs` (colocated with Schema/Field/Type)
- `functions!` - Defined in `src/ast/scalar.rs` (colocated with Function)
- Operators (`filter!`, `project!`, etc.) - Defined in `src/ast/relational.rs` (colocated with Rel)
- `rule!` - Defined in `src/rule/mod.rs` (colocated with RewriteRule trait)

### Key Techniques

1. **Token tree matching**: `$expr:tt` captures individual tokens, `$($expr:tt)*` captures sequences
2. **Recursion**: `functions!` macro processes functions one at a time recursively
3. **Optional modifiers**: `$($modifier:ident)?` allows optional nullability specifiers
4. **Helper macros**: Internal `__parse_*` macros handle expression parsing
5. **`stringify!()`**: Converts identifiers to strings for naming

## Testing

Run the example to see all macros in action:
```bash
cargo run --example test_rule_macro
```

This demonstrates:
- ✅ Schema creation with various nullability options
- ✅ Function declarations
- ✅ Filter and projection operators
- ✅ Complete rule definitions
- ✅ All 4 existing rules rewritten using macros

## Future Enhancements

1. **Support nested function calls** - Would require more sophisticated parsing
2. **Better error messages** - Currently macro errors can be cryptic
3. **More operator support** - Sort, window functions, etc.
4. **Constraint syntax** - For rules with additional conditions
5. **Meta-variable support** - For rule families (like JoinAssociate with different join types)
