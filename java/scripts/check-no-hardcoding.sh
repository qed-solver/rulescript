#!/bin/bash

set -u
cd "$(dirname "$0")/.."

CALCITE_DIR=src/main/java/org/qed/Backends/Calcite
RULES_DIR=src/main/java/org/qed/RRuleInstances
status=0

sources=$(find "$CALCITE_DIR" -name '*.java' \
    -not -path "$CALCITE_DIR/Generated/*" -not -path "$CALCITE_DIR/Tests/*" \
    -not -name CalciteTester.java)

for pattern in 'rulename' 'ruleName' 'rule\.name' 'RRuleInstances' 'getSimpleName' 'getClass\(\)\.getName'; do
    if grep -nE "$pattern" $sources; then
        echo "error: generator source matches forbidden pattern: $pattern"
        status=1
    fi
done

for rule in $(ls "$RULES_DIR" | sed 's/\.java$//'); do
    if grep -nwE "$rule" $sources; then
        echo "error: generator source mentions rule name: $rule"
        status=1
    fi
done

if grep -nE 'equals\("[A-Z][A-Za-z]+"\)|case "[A-Z][A-Za-z]+"' $sources; then
    echo "error: generator source switches on a string that looks like a rule name"
    status=1
fi

exit $status
