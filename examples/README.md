# RuleScript Interactive Optimizer

Interactive demonstration of query optimization rules with dynamic rule selection.

## Running the Optimizer

```bash
cargo run --example optimizer
```

Or with verbose mode enabled:
```bash
cargo run --example optimizer -- --verbose
```

## How It Works

1. **Select Rules** - Choose which optimization rules to apply (by number)
2. **Enter SQL** - Type SQL queries to see how rules optimize them
3. **See Transformation** - View before/after logical plans
4. **Switch Rules** - Change active rules anytime during the session

The optimizer uses DataFusion's `Optimizer` with `RuleWrapper` to apply rules recursively throughout the plan tree.

## Available Rules

1. **filter-project-transpose** - Push filter below projection
2. **project-merge** - Merge consecutive projections  
3. **filter-merge** - Merge consecutive filters
4. **project-remove** - Remove identity projections

## Available Tables

- **emp** - Employee table (empno, ename, job, mgr, hiredate, salary, commission, deptno)
- **dept** - Department table (deptno, dname, loc)

## Example Session

```
Available Rules:
  1. filter-project-transpose - Push filter below projection
  2. project-merge - Merge consecutive projections
  3. filter-merge - Merge consecutive filters
  4. project-remove - Remove identity projections
  5. all - Enable all rules

Select rules (comma-separated numbers, e.g., 1,2,3 or 'all'): 1,2

sql> SELECT * FROM (SELECT salary * 1.1 AS raised, deptno FROM emp) WHERE raised > 55000

🔍 Logical Plan (BEFORE optimization):
Projection: raised, emp.deptno
  Filter: raised > Float64(55000)
    Projection: emp.salary * Float64(1.1) AS raised, emp.deptno
      TableScan: emp

✨ Logical Plan (AFTER optimization):
Projection: raised, emp.deptno
  Projection: emp.salary * Float64(1.1) AS raised, emp.deptno
    Filter: emp.salary * Float64(1.1) > Float64(55000)
      TableScan: emp

✅ Query plan optimized!

sql> set 3,4
✓ Active rules: filter-merge, project-remove

sql> quit
Goodbye!
```

## REPL Commands

- **SQL query** - Type any SQL query to see optimization
- `rules` - Show active and available rules with checkmarks
- `set <numbers>` - Activate specific rules (e.g., `set 1,2,3`)
- `set all` - Enable all rules
- `clear` - Deactivate all rules
- `verbose` - Toggle verbose error messages
- `help` - Show command help
- `quit` / `exit` / Ctrl+D - Exit the REPL
- `Ctrl+C` - Cancel current input

## Features

- ✅ **Dynamic rule selection** - Choose and switch rules interactively
- ✅ **Multiple rules** - Apply multiple rules together
- ✅ **Recursive optimization** - Rules apply throughout the plan tree
- ✅ **Line editing** - Arrow keys, history (↑/↓)
- ✅ **Verbose mode** - Toggle detailed error messages
- ✅ **Real SQL parsing** - Uses DataFusion's SQL parser

## Example Queries

### Filter-Project Transpose
```sql
SELECT * FROM (SELECT salary * 1.1 AS raised FROM emp) WHERE raised > 55000
SELECT * FROM (SELECT salary, commission * 2 AS doubled FROM emp) WHERE doubled < 1000
```

### Project Merge
```sql
SELECT result FROM (SELECT x * 2 AS result FROM (SELECT salary + 1000 AS x FROM emp))
```

### Filter Merge
```sql
SELECT * FROM emp WHERE deptno = 10 AND salary > 50000
```

## Architecture

```
examples/
├── optimizer.rs          # Main entry point
└── optimizer_repl/
    ├── mod.rs           # REPL logic with rule selection
    └── tables.rs        # Table definitions
```
