// SCOPE: FULL
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record NegateAnd() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN left = source.pred("left");
    static final RexRN right = source.pred("right");

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Not(RexRN.and(left, right)));
    }

    @Override
    public RelRN after() {
        return source.filter(new RexRN.Or(Seq.of(new RexRN.Not(left), new RexRN.Not(right))));
    }
}
