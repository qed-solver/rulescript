package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: FULL
public record SimplifyOrTrue() implements RRule {

    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN left = source.pred("left");

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Or(Seq.of(left, RexRN.trueLiteral())));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.trueLiteral());
    }
}