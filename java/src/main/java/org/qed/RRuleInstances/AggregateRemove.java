package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record AggregateRemove() implements RRule {
    static final RelRN source = RelRN.uniqueScan("Source", "Source_Type");

    @Override
    public RelRN before() {
        return source.distinct();
    }

    @Override
    public RelRN after() {
        return source;
    }
}
