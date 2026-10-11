package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

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
