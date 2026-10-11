package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — assumes Or(False, right) is the entire predicate of a Filter over a single scan, not an arbitrary boolean context
public record SimplifyFalseOr() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN right = source.pred("right");

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Or(Seq.of(RexRN.falseLiteral(), right)));
    }

    @Override
    public RelRN after() {
        return source.filter(right);
    }
}
