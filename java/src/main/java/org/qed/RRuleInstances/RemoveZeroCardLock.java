package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// SCOPE: PARTIAL — Lock is modeled as an uninterpreted filter predicate and the HasZeroRows precondition as a structurally empty input, since the DSL has no Lock operator or cardinality constraint
public record RemoveZeroCardLock() implements RRule {
    static final RelRN zeroInput = RelRN.scan("Input", "Input_Type").empty();

    @Override
    public RelRN before() {
        return zeroInput.filter("lock");
    }

    @Override
    public RelRN after() {
        return zeroInput;
    }
}
