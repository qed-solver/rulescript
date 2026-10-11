package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: FULL
public record SimplifyAndFalse() implements RRule {

    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN left = source.pred("left");

    @Override
    public RelRN before() {
        return source.filter(RexRN.and(left, RexRN.falseLiteral()));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.falseLiteral());
    }
}