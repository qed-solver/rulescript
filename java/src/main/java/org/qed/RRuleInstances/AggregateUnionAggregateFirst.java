package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record AggregateUnionAggregateFirst() implements RRule {
    static final RelRN left = RelRN.scan("Left", "Common_Type");
    static final RelRN right = RelRN.scan("Right", "Common_Type");

    @Override
    public RelRN before() {
        return left.distinct().union(true, right).distinct();
    }

    @Override
    public RelRN after() {
        return left.union(true, right).distinct();
    }
}
