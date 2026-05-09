# ProxySQL

This directory contains ProxySQL-specific rewrite artifacts and test helpers.

## Status

This backend is currently legacy and is retained for compatibility and prior experiments.

## Generated and test files

Generated SQL rules are in:

- `src/main/java/org/qed/Backends/ProxySQL/Generated/*.sql`

Test SQL and helper scripts are in:

- `src/main/java/org/qed/Backends/ProxySQL/Tests/*Test.sql`
- `src/main/java/org/qed/Backends/ProxySQL/Tests/script-proxysql.sh`

## Running ProxySQL tests

Use the checked-in generated SQL and run the helper script:

```sh
cd src/main/java/org/qed/Backends/ProxySQL/Tests
bash script-proxysql.sh
```

The script expects local ProxySQL/MySQL endpoints and credentials defined at the top of the file.