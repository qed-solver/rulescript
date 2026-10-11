package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: FULL
public record SimplifyTrueAnd() implements RRule {

    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN right = source.pred("right");

    @Override
    public RelRN before() {
        return source.filter(RexRN.and(RexRN.trueLiteral(), right));
    }

    @Override
    public RelRN after() {
        return source.filter(right);
    }
}
