package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// SCOPE: FULL
public record UnionEliminator() implements RRule {
    static final RelRN input = RelRN.scan("Input", "Input_Type");

    @Override
    public RelRN before() {
        return input.union(true);
    }

    @Override
    public RelRN after() {
        return input;
    }
}
