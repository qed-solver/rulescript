# MySQL

This directory contains MySQL-specific artifacts produced from RuleScript rules.

## Status

This backend is currently legacy and is kept for compatibility and historical validation.

## Generate Rules

Run the MySQL generator from the repository root:

```sh
./mvnw -q compile exec:java@mysql-tester
```

Generated SQL files are written to:

- `src/main/java/org/qed/Backends/MySQL/Generated/*.sql`

MySQL test templates are stored in:

- `src/main/java/org/qed/Backends/MySQL/Tests/*Test.sql`