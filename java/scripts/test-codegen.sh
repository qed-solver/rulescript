#!/bin/bash

SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/null}"
OUTPUT="$(mktemp)"
trap 'rm -f "$OUTPUT"' EXIT

echo "## Code Generation Test Results" >> "$SUMMARY"
echo "" >> "$SUMMARY"

./scripts/check-no-hardcoding.sh || exit 1

missing=""
for rule_file in src/main/java/org/qed/RRuleInstances/*.java; do
    rule_name=$(basename "$rule_file" .java)
    if [ ! -f "src/main/java/org/qed/Backends/Calcite/Tests/${rule_name}Test.java" ]; then
        missing="${missing}- ${rule_name}\n"
    fi
done
if [ -n "$missing" ]; then
    echo "**Missing tests:**" >> "$SUMMARY"
    echo -e "$missing" >> "$SUMMARY"
    echo -e "Missing tests:\n$missing" >&2
    exit 1
fi

MAVEN_CP=$(./mvnw dependency:build-classpath -Dmdep.outputFile=/dev/stdout -q)
java -cp "target/classes:${MAVEN_CP}" org.qed.Backends.Calcite.CalciteTester 2>&1 | tee "$OUTPUT"
status=${PIPESTATUS[0]}

failed=$(grep -c '^failed' "$OUTPUT")
passed=$(grep -c '^succeeded' "$OUTPUT")
{
    echo "$(tail -n 1 "$OUTPUT")"
    echo ""
    echo "Checks passed: ${passed}, failed: ${failed}"
    grep -E '^Failed to generate|^Generated rule does not compile' "$OUTPUT"
} >> "$SUMMARY"

if [ -n "$(git status --porcelain -- src/main/java/org/qed/Backends/Calcite/Generated 2>/dev/null)" ]; then
    echo "Generated/ differs from the generator output; regenerate and commit it" >&2
    git status --short -- src/main/java/org/qed/Backends/Calcite/Generated >&2
    echo "**Generated/ is out of date**" >> "$SUMMARY"
    status=1
fi

exit $status
