package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: FULL
public record MergeSelects() implements RRule {
    static final RelRN input = RelRN.scan("input", "input_type");
    static final RexRN inner = input.pred("innerFilters");
    static final RexRN outer = input.pred("filters");

    @Override
    public RelRN before() {
        return input.filter(inner).filter(outer);
    }

    @Override
    public RelRN after() {
        return input.filter(RexRN.and(inner, outer));
    }
}
