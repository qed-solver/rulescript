package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: FULL
public record FoldNotFalse() implements RRule {

    static final RelRN source = RelRN.scan("Source", "Source_Type");

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Not(RexRN.falseLiteral()));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.trueLiteral());
    }
}
