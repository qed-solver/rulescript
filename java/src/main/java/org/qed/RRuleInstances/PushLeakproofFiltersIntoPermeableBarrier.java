package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — the barrier's input is fixed to a single column; the original rule applies to any arity
public record PushLeakproofFiltersIntoPermeableBarrier() implements RRule {
    // Barrier modeled as an identity projection (same as EliminateRedundantBarrier).
    // The LeakproofPermeable flag is a flow-control knob with no bag-semantic
    // effect on row values, so it is not modeled.
    static final RelRN input = RelRN.scan("Input", "Input_Type");
    static final RelRN barrier = input.project(input.field(0));

    // Before: full filter above the barrier, split into the leakproof group
    // and the remaining group (the rule's SplitLeakproofFilters result).
    static final RexRN leakBefore = barrier.field(0).pred("Leakproof");
    static final RexRN remBefore = barrier.field(0).pred("Remaining");

    // After: only the leakproof group is pushed beneath the barrier; the
    // remaining group stays above it.
    static final RexRN leakBelow = input.field(0).pred("Leakproof");
    static final RelRN filtered = input.filter(leakBelow);
    static final RelRN barrierAfter = filtered.project(filtered.field(0));
    static final RexRN remAfter = barrierAfter.field(0).pred("Remaining");

    @Override
    public RelRN before() {
        return barrier.filter(RexRN.and(leakBefore, remBefore));
    }

    @Override
    public RelRN after() {
        return barrierAfter.filter(remAfter);
    }
}
