package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RRule;

public record UnionFilterToFilter() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN leftCondition = source.pred("leftCondition");
    static final RexRN rightCondition = source.pred("rightCondition");

    @Override public RelRN before() {
        return source.filter(leftCondition).union(false, source.filter(rightCondition));
    }

    @Override public RelRN after() {
        return source.filter(new RexRN.Or(Seq.of(leftCondition, rightCondition))).distinct();
    }
}
