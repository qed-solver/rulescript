#!/bin/bash

set -u
cd "$(dirname "$0")/.."

SPARK_JAVA=src/main/java/org/qed/Backends/Spark
RULES_DIR=src/main/java/org/qed/RRuleInstances
status=0

sources=$(find "$SPARK_JAVA/Runtime" "$SPARK_JAVA/SparkGenerator.java" -name '*.java')

for pattern in 'rulename' 'ruleName' 'RRuleInstances' 'getSimpleName\(\)\s*\.(equals|startsWith|endsWith|contains)' 'name\(\)\s*\.(equals|startsWith|endsWith|contains|matches)'; do
    if grep -nE "$pattern" $sources | grep -v 'public String ruleName()'; then
        echo "error: Spark source matches forbidden pattern: $pattern"
        status=1
    fi
done

for rule in $(ls "$RULES_DIR" | sed 's/\.java$//'); do
    if grep -nw "$rule" $sources; then
        echo "error: Spark source mentions rule name: $rule"
        status=1
    fi
done

if grep -nE 'equals\("[A-Z][A-Za-z]+"\)|case "[A-Z][A-Za-z]+"' $sources; then
    echo "error: Spark source switches on a string that looks like a rule name"
    status=1
fi

exit $status
