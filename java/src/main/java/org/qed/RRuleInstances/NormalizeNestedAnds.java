package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — assumes $left, $innerLeft, $innerRight are each atomic (non-And) predicates
public record NormalizeNestedAnds() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN left = source.pred("left");
    static final RexRN innerLeft = source.pred("innerLeft");
    static final RexRN innerRight = source.pred("innerRight");

    @Override
    public RelRN before() {
        // p AND (q AND r)
        return source.filter(RexRN.and(left, RexRN.and(innerLeft, innerRight)));
    }

    @Override
    public RelRN after() {
        // (p AND q) AND r
        return source.filter(RexRN.and(RexRN.and(left, innerLeft), innerRight));
    }
}
