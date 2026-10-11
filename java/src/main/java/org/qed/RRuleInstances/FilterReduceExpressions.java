package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the filter condition is the constant FALSE literal (the branch where constant reduction of the condition yields a constant false, so the filter is replaced by an empty relation with the input's row type via createEmptyRelOrEquivalent)
public record FilterReduceExpressions() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    @Override
    public RelRN before() {
        return source.filter(RexRN.falseLiteral());
    }

    @Override
    public RelRN after() {
        return source.empty();
    }
}
