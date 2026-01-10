# RuleScript Examples

This directory contains examples demonstrating RuleScript's query optimization capabilities.

## Examples Overview

| Example | Description | Command |
|---------|-------------|---------|
| `optimizer` | Interactive SQL REPL with rule selection | `cargo run --example optimizer` |
| `tpch_benchmark` | TPC-H benchmark comparing RuleScript vs baseline | `cargo run --release --example tpch_benchmark` |
| `tpch_optimize` | Apply rules to TPC-H queries, show transformations | `cargo run --example tpch_optimize` |
| `export_rules_to_qed` | Export rules to QED JSON format for verification | `cargo run --example export_rules_to_qed` |
| `user_defined_left_semi_join` | User-defined operator example with EXISTS semantics | `cargo run --example user_defined_left_semi_join` |

## Interactive Optimizer REPL

```bash
cargo run --example optimizer
```

An interactive SQL REPL where you can:
- Select which optimization rules to apply
- Switch rules during the session
- See how rules transform query plans

### Commands

- **SQL query** - Type any SQL query to see optimization
- `rules` - Show active and available rules
- `help <number>` - Show detailed explanation for a rule
- `try <number>` - Run the example query for a rule
- `set <numbers>` - Activate rules (e.g., `set 1,2,3` or `set all`)
- `clear` - Deactivate all rules
- `verbose` - Toggle verbose mode
- `quit` / `exit` - Exit

### Example Session

```
sql> set 1,2

sql> SELECT * FROM (SELECT salary * 1.1 AS raised FROM emp) WHERE raised > 55000

🔍 Logical Plan (BEFORE optimization):
Filter: raised > Float64(55000)
  Projection: emp.salary * Float64(1.1) AS raised
    TableScan: emp

✨ Logical Plan (AFTER optimization):
Projection: emp.salary * Float64(1.1) AS raised
  Filter: emp.salary * Float64(1.1) > Float64(55000)
    TableScan: emp
```

## TPC-H Benchmark

```bash
# Generate TPC-H data first (SF=1, ~1GB)
cd TPC-H\ V3.0.1/dbgen && make && ./dbgen -s 1
mkdir -p ../../data/tpch-sf1 && mv *.tbl ../../data/tpch-sf1/

# Run benchmark
cargo run --release --example tpch_benchmark -- -p data/tpch-sf1 -n 3

# Compare optimized plans
cargo run --release --example tpch_benchmark -- -p data/tpch-sf1 --compare-plans

# Run single query
cargo run --release --example tpch_benchmark -- -p data/tpch-sf1 -q 18
```

## TPC-H Optimization Demo

```bash
# Show all query transformations
cargo run --example tpch_optimize -- -v

# Export to QED format
cargo run --example tpch_optimize -- --export
```

Shows how RuleScript rules transform unoptimized TPC-H query plans:
- 14 queries optimized (multi-table comma-joins)
- 8 queries unchanged (single table, correlated subqueries, LEFT JOIN)

## QED Export

```bash
cargo run --example export_rules_to_qed
```

Exports all RuleScript rules to QED JSON format for formal verification. See [QED Solver](https://github.com/qed-solver) for verification instructions.

## Available Rules

| # | Rule | Description |
|---|------|-------------|
| 1 | filter-project-transpose | Push filter below projection |
| 2 | project-merge | Merge consecutive projections |
| 3 | filter-merge | Merge consecutive filters |
| 4 | project-remove | Remove identity projections |
| 5 | join-commute | Swap join inputs |
| 6 | filter-into-join | Merge filter into join condition |
| 7 | join-condition-push | Push join predicates to inputs |
| 8 | join-extract-filter | Extract join condition to filter |
| 9 | join-left-project-transpose | Pull projection from left join input |
| 10 | join-right-project-transpose | Pull projection from right join input |
| 11 | join-associate | Restructure nested joins |
| 12 | left-semi-join-filter-transpose | Push filter through left semi-join |
| 13 | filter-reduce-true | Remove `Filter(_, true)` |
| 14 | filter-reduce-false | Replace `Filter(_, false)` with Empty |
| 15-17 | prune-empty-* | Propagate empty relations |

## User-Defined Operator Example

```bash
cargo run --example user_defined_left_semi_join
```

Demonstrates defining a custom `LeftSemiJoin` operator that works as both:
1. A pattern (via `UserDefinedLogicalOperator` trait) with EXISTS semantics
2. A concrete DataFusion plan (via `UserDefinedLogicalNodeCore` trait)

Key concepts:
- Building EXISTS subquery in `semantics()`
- QED serialization for verification
- Proper context handling in resolve/instantiate

## Directory Structure

```
examples/
├── optimizer.rs                    # Interactive REPL entry point
├── optimizer_repl/                 # REPL implementation
│   ├── mod.rs                     # Main REPL logic
│   ├── tables.rs                  # Table definitions (emp, dept, sales)
│   └── wrappers.rs                # Rule wrappers for DataFusion
├── tpch/                           # TPC-H support
│   ├── mod.rs                     # Module exports
│   ├── queries.rs                 # All 22 TPC-H queries
│   └── schema.rs                  # TPC-H table schemas
├── tpch_benchmark.rs               # TPC-H performance benchmark
├── tpch_optimize.rs                # TPC-H optimization demo
├── export_rules_to_qed.rs          # QED export tool
├── user_defined_left_semi_join.rs  # User-defined operator example
└── README.md                       # This file
```
