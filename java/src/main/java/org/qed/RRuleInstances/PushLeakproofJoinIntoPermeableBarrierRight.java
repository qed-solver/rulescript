package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;

// SCOPE: PARTIAL — both join inputs fixed to two columns and the join fixed to a plain inner join (the source rule applies to any arity and also matches the apply-join variant)
public record PushLeakproofJoinIntoPermeableBarrierRight() implements RRule {
    // Leakproof left input: two uninterpreted columns (l0, l1).
    static final RelRN left = RelRN.scanMany("L", Seq.of(
            new RelType.VarType("L0_Type", true),
            new RelType.VarType("L1_Type", true)));

    // Right input beneath the permeable barrier: two uninterpreted columns
    // (r0, r1). The Barrier is modeled as an identity projection (same as
    // the PushLeakproofFiltersIntoPermeableBarrier precedent); the
    // LeakproofPermeable flag and the IsLeakproof / HasAllLeakProofFilters
    // preconditions are flow-control/safety knobs with no effect on the row
    // bag, so they are not modeled.
    static final RelRN right = RelRN.scanMany("R", Seq.of(
            new RelType.VarType("R0_Type", true),
            new RelType.VarType("R1_Type", true)));
    static final RelRN barrierRight = right.project(right.fields());

    // Shared uninterpreted ON predicate C over the join's columns
    // (l0, l1, r0, r1) — stands in for the conjunction of the rule's
    // leakproof ON filters. Reusing the name "C" on both sides tells QED
    // the two occurrences are the same symbol.
    //
    // Before: InnerJoin(L, Barrier(R), C) — the join sits above the barrier.
    static final RelRN before =
            left.join(JoinRelType.INNER, left.joinPred("C", barrierRight), barrierRight);

    // After: the join is pushed below the barrier, which is rewrapped above
    // it: Barrier(InnerJoin(L, R, C)) — identity projection over the join.
    static final RelRN joinAfter =
            left.join(JoinRelType.INNER, left.joinPred("C", right), right);
    static final RelRN after = joinAfter.project(joinAfter.fields());

    @Override
    public RelRN before() {
        return before;
    }

    @Override
    public RelRN after() {
        return after;
    }
}
