#!/bin/bash
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COCKROACH_DIR="${COCKROACH_DIR:-$here/vendor/cockroach}"
backend="$here/src/main/java/org/qed/Backends/Cockroach"

if [ ! -d "$COCKROACH_DIR/pkg/sql/opt" ]; then
    echo "error: no CockroachDB checkout at $COCKROACH_DIR; run scripts/setup-cockroach.sh first" >&2
    exit 1
fi

cd "$here"
./scripts/check-no-hardcoding-cockroach.sh
if [ "${SKIP_CODEGEN:-0}" != "1" ]; then
    ./mvnw -q compile exec:java@cockroach-codegen 2>&1 | grep -v WARNING
fi

dest="$COCKROACH_DIR/pkg/sql/opt/rulescript"
mkdir -p "$dest/testdata/rules"
rm -f "$dest"/*.go "$dest"/testdata/rules/*.json
cp "$backend"/Runtime/*.go "$backend"/Runtime/BUILD.bazel "$backend"/Tests/*.go "$dest/"
cp "$backend"/Generated/*.json "$dest/testdata/rules/"

cd "$COCKROACH_DIR"
./dev test pkg/sql/opt/rulescript -v "$@"
