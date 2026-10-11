package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — assumes the Or-with-False occurs as the entire filter predicate, not nested inside another boolean operator or used as a join condition
public record SimplifyOrFalse() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN left = source.pred("left");

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Or(Seq.of(left, RexRN.falseLiteral())));
    }

    @Override
    public RelRN after() {
        return source.filter(left);
    }
}
