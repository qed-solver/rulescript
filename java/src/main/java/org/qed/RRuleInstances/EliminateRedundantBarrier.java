package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// SCOPE: PARTIAL — the barrier's input is fixed to a single column; the original rule applies to any arity
public record EliminateRedundantBarrier() implements RRule {
    static final RelRN input = RelRN.scan("Input", "Input_Type");
    static final RelRN innerBarrier = input.project(input.field(0));

    @Override
    public RelRN before() {
        return innerBarrier.project(innerBarrier.field(0));
    }

    @Override
    public RelRN after() {
        return innerBarrier;
    }
}