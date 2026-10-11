package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// SCOPE: FULL
public record EliminateSelect() implements RRule {

    static final RelRN source = RelRN.scan("Source", "Source_Type");

    @Override
    public RelRN before() {
        return source.filter(org.qed.RexRN.trueLiteral());
    }

    @Override
    public RelRN after() {
        return source;
    }
}
