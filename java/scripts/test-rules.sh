#!/bin/bash

# Test all generated rules with qed-prover

if [ -z "${GITHUB_STEP_SUMMARY:-}" ]; then
    GITHUB_STEP_SUMMARY="tmp-rules/qed-prover-step-summary.md"
fi
mkdir -p "$(dirname "$GITHUB_STEP_SUMMARY")"

log_line() {
    printf '%s\n' "$@"
    printf '%s\n' "$@" >> "$GITHUB_STEP_SUMMARY"
}

log_line "## QED Prover Test Results"
log_line ""

failed_rules=""
total_count=0
passed_count=0

for json_file in tmp-rules/*.json; do
    rule_name=$(basename "$json_file" .json)
    total_count=$((total_count + 1))
    ./qed-prover/target/release/qed-prover "$json_file" || true

    result_file="${json_file%.json}.result"
    if [ -f "$result_file" ] && jq -e '.provable == true' "$result_file" > /dev/null 2>&1; then
        log_line "✅ $rule_name: PASSED"
        passed_count=$((passed_count + 1))
    else
        log_line "❌ $rule_name: FAILED"
        failed_rules="$failed_rules$rule_name,"
    fi
done

log_line ""
log_line "**Summary:** $passed_count/$total_count passed"

if [ -n "$failed_rules" ]; then
    msg="Failed rules: ${failed_rules%,}"
    echo "$msg" >&2
    if [ -n "${GITHUB_ACTIONS:-}" ]; then
        echo "::error::$msg"
    fi
    exit 1
fi
