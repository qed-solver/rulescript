#!/bin/bash
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
COCKROACH_DIR="${COCKROACH_DIR:-$here/vendor/cockroach}"
COMMIT=d30c905fff79ef825adc96bcc647f1872a90f2ff
patch="$here/src/main/java/org/qed/Backends/Cockroach/cockroach.patch"

if ! command -v bazelisk >/dev/null 2>&1 && ! command -v bazel >/dev/null 2>&1; then
    echo "error: bazelisk (or bazel) is required; on macOS: brew install bazelisk" >&2
    exit 1
fi
if [ "$(uname)" = "Darwin" ] && ! xcode-select -p >/dev/null 2>&1; then
    echo "error: Xcode command line tools are required: xcode-select --install" >&2
    exit 1
fi

if [ ! -d "$COCKROACH_DIR/.git" ]; then
    mkdir -p "$COCKROACH_DIR"
    git -C "$COCKROACH_DIR" init -q
    git -C "$COCKROACH_DIR" remote add origin https://github.com/cockroachdb/cockroach.git
fi
if [ "$(git -C "$COCKROACH_DIR" rev-parse HEAD 2>/dev/null || true)" != "$COMMIT" ]; then
    git -C "$COCKROACH_DIR" fetch --depth 1 origin "$COMMIT"
    git -C "$COCKROACH_DIR" checkout -q --detach FETCH_HEAD
fi
git -C "$COCKROACH_DIR" submodule update --init --recursive --depth 1

if git -C "$COCKROACH_DIR" apply --check "$patch" 2>/dev/null; then
    git -C "$COCKROACH_DIR" apply "$patch"
elif ! git -C "$COCKROACH_DIR" apply --reverse --check "$patch" 2>/dev/null; then
    echo "error: cockroach.patch does not apply to $COCKROACH_DIR" >&2
    exit 1
fi

tmp="$(dirname "$COCKROACH_DIR")/bazel-tmp"
mkdir -p "$tmp"
for line in "test --test_tmpdir=$tmp" "build --config=dev" "build --config=nolintonbuild"; do
    grep -qxF "$line" "$COCKROACH_DIR/.bazelrc.user" 2>/dev/null || echo "$line" >> "$COCKROACH_DIR/.bazelrc.user"
done

(cd "$COCKROACH_DIR" && ./dev doctor --interactive=false --no-cache </dev/null)
echo "ready: $COCKROACH_DIR"
