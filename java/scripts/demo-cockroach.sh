#!/bin/bash
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COCKROACH_DIR="${COCKROACH_DIR:-$here/vendor/cockroach}"
backend="$here/src/main/java/org/qed/Backends/Cockroach"

cd "$here"
./mvnw -q compile exec:java@cockroach-codegen 2>&1 | grep -v WARNING || true
mkdir -p "$COCKROACH_DIR/pkg/sql/opt/rulescript"
cp "$backend"/Runtime/*.go "$backend"/Runtime/BUILD.bazel "$COCKROACH_DIR/pkg/sql/opt/rulescript/"

cd "$COCKROACH_DIR"
./dev build cockroach-short

native="$(grep -hoE '^\[[A-Za-z0-9]+' pkg/sql/opt/norm/rules/*.opt | tr -d '[' | sort -u | paste -sd, -)"
sql="$(mktemp)"
trap 'rm -f "$sql"' EXIT
{
    echo "CREATE TABLE t (a INT PRIMARY KEY, b INT, c INT);"
    echo "CREATE TABLE u (x INT PRIMARY KEY, y INT);"
    while IFS= read -r query; do
        echo "SET disable_optimizer_rules = '$native,ExternalNormalizeRule';"
        echo "SELECT 'OFF: $query' AS plan;"
        echo "EXPLAIN (OPT) $query;"
        echo "SET disable_optimizer_rules = '$native';"
        echo "SELECT 'ON:  $query' AS plan;"
        echo "EXPLAIN (OPT) $query;"
    done <<'QUERIES'
SELECT * FROM t WHERE (b > 0) = NULL::BOOL
SELECT * FROM (SELECT a, b, c FROM t) q WHERE a = b OR a = c OR a = b OR a = c
SELECT * FROM t WHERE EXISTS (SELECT 1 FROM u WHERE false)
SELECT * FROM (SELECT * FROM (SELECT * FROM t WHERE a > 0) WHERE b > 0) WHERE a > 0
QUERIES
} > "$sql"

COCKROACH_RULESCRIPT_DIR="$backend/Generated" ./cockroach-short demo --no-example-database --insecure -f "$sql" 2>&1 \
    | grep -v -E '^(NOTICE|SET|CREATE|info|plan)'
