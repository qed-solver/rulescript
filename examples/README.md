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

Commands:
  • Type SQL query to see optimization
  • 'rules' - Show active and available rules
  • 'help <number>' - Show detailed explanation for a rule (e.g., 'help 1')
  • 'try <number>' - Run the example query for a rule (e.g., 'try 1')
  • 'set <numbers>' - Activate rules (e.g., 'set 1,2,3' or 'set all')
  • 'clear' - Deactivate all rules
  • 'verbose' - Toggle verbose mode
  • 'help' - Show this message
  • 'quit' or 'exit' or Ctrl+D - Exit

📊 Current Configuration:
  • Rules: None
  • Verbose: OFF

sql> set 1,2

📊 Current Configuration:
  • Rules: filter-project-transpose, project-merge
  • Verbose: OFF

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

sql> help 1

╔══════════════════════════════════════════════════════════════════════════╗
║ Rule 1: filter-project-transpose
╚══════════════════════════════════════════════════════════════════════════╝

📝 Description:
   Push filter below projection

🔍 How it works:
   Pushes a filter condition below a projection by rewriting the filter to use the projection's input columns. This enables earlier filtering of data before computing expensive expressions.
   Pattern: Filter(P(y), Project(f(x), source)) → Project(f(x), Filter(P(f(x)), source))

💡 Example query:
   -- Filter on projected column gets pushed below projection
   SELECT * FROM (SELECT salary * 1.1 AS raised, deptno FROM emp) WHERE raised > 55000

💻 Tip: Use 'try 1' to run this example (rule must be enabled with 'set 1')

sql> set 1

📊 Current Configuration:
  • Rules: filter-project-transpose
  • Verbose: OFF

sql> try 1

🚀 Running example for rule 1:

SELECT * FROM (
    SELECT salary * 1.1 AS raised, deptno 
    FROM emp
) WHERE raised > 55000

🔍 Logical Plan (BEFORE optimization):
Projection: raised, deptno
  Filter: raised > Float64(55000)
    Projection: emp.salary * Float64(1.1) AS raised, emp.deptno
      TableScan: emp

✨ Logical Plan (AFTER optimization):
Projection: raised, deptno
  Projection: emp.salary * Float64(1.1) AS raised, emp.deptno
    Filter: emp.salary * Float64(1.1) > Float64(55000)
      TableScan: emp

✅ Query plan optimized!

sql> verbose

📊 Current Configuration:
  • Rules: filter-project-transpose, project-merge
  • Verbose: ON

sql> clear

📊 Current Configuration:
  • Rules: None
  • Verbose: ON

sql> quit
Goodbye!
```

## REPL Commands

- **SQL query** - Type any SQL query to see optimization
- `rules` - Show active and available rules with checkmarks
- `help <number>` - Show detailed explanation and example for a rule (e.g., `help 1`)
- `try <number>` - Run the example query for a rule (e.g., `try 1`)
- `set <numbers>` - Activate specific rules (e.g., `set 1,2,3`)
- `set all` - Enable all rules
- `clear` - Deactivate all rules
- `verbose` - Toggle verbose error messages
- `help` - Show command help
- `quit` / `exit` / Ctrl+D - Exit the REPL
- `Ctrl+C` - Cancel current input

## Features

- ✅ **Dynamic rule selection** - Choose and switch rules interactively
- ✅ **Multiple rules** - Apply multiple rules together (rules often work best in combination)
- ✅ **Recursive optimization** - Rules apply throughout the plan tree
- ✅ **Line editing** - Arrow keys, history (↑/↓)
- ✅ **Verbose mode** - Toggle detailed error messages
- ✅ **Real SQL parsing** - Uses DataFusion's SQL parser

## Tips

- **Combine rules**: Some optimizations work best when multiple rules are enabled together. For example:
  - `set 3,4` - filter-merge + project-remove (removes identity projections that block filter merge)
  - `set 1,2` - filter-project-transpose + project-merge (pushes filters and merges projections)
  - `set all` - Enable all rules to see comprehensive optimization

- **DataFusion adds projections**: The SQL parser often adds `SELECT *` projections which can interfere with some rules. Use specific column lists or enable `project-remove` to clean them up.

## Example Queries

### Filter-Project Transpose
```sql
-- Filter gets pushed below the projection
SELECT * FROM (
    SELECT salary * 1.1 AS raised, deptno 
    FROM emp
) WHERE raised > 55000
```

### Project Merge
```sql
-- Two projections merged into one
SELECT doubled FROM (
    SELECT increased * 2 AS doubled FROM (
        SELECT salary + 1000 AS increased 
        FROM emp
    )
)
```

### Filter Merge
```sql
-- Nested filters merged into one
-- Note: Also enable project-remove (set 3,4) to see full optimization
SELECT empno, salary FROM (
    SELECT empno, salary FROM emp 
    WHERE deptno = 10
) WHERE salary > 50000
```

## Architecture

```
examples/
├── optimizer.rs          # Main entry point
└── optimizer_repl/
    ├── mod.rs           # REPL logic with rule selection
    └── tables.rs        # Table definitions
```
