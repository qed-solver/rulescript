#!/bin/bash

set -u
cd "$(dirname "$0")/.."

COCKROACH_JAVA=src/main/java/org/qed/Backends/Cockroach
RULES_DIR=$COCKROACH_JAVA/Rules
GO_DIR=$COCKROACH_JAVA/Runtime
status=0

java_sources=$(find "$COCKROACH_JAVA" -name '*.java' -not -path "$RULES_DIR/*" -not -name CockroachTester.java)
go_sources=$(find "$GO_DIR" -name '*.go')

for pattern in 'rulename' 'ruleName' 'RRuleInstances' 'Cockroach\.Rules' 'name\(\)\s*\.(equals|startsWith|endsWith|contains|matches)' 'switch\s*\([^)]*name\(\)' 'getSimpleName\(\)\s*\.(equals|startsWith|endsWith|contains)'; do
    if grep -nE "$pattern" $java_sources; then
        echo "error: generator source matches forbidden pattern: $pattern"
        status=1
    fi
done

for rule in $(ls "$RULES_DIR" | sed 's/\.java$//'); do
    if grep -nw "$rule" $java_sources $go_sources; then
        echo "error: source mentions rule name: $rule"
        status=1
    fi
done

if grep -nE 'equals\("[A-Z][A-Za-z]+"\)|case "[A-Z][A-Za-z]+"' $java_sources; then
    echo "error: generator source switches on a string that looks like a rule name"
    status=1
fi

exit $status
