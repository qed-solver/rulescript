package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: FULL
public record SimplifyTrueOr() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN right = source.pred("right");

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Or(Seq.of(RexRN.trueLiteral(), right)));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.trueLiteral());
    }
}
