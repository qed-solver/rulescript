package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RRule;

public record IntersectFilterToFilter() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN leftCondition = source.pred("leftCondition");
    static final RexRN rightCondition = source.pred("rightCondition");

    @Override public RelRN before() {
        return source.filter(leftCondition).intersect(false, source.filter(rightCondition));
    }

    @Override public RelRN after() {
        return source.filter(RexRN.and(leftCondition, rightCondition)).distinct();
    }
}
