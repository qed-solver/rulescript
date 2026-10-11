package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;

// SCOPE: PARTIAL — both join inputs fixed to two columns and the join fixed to a plain inner join (the source rule applies to any arity and also matches the apply-join variant)
public record PushLeakproofJoinIntoPermeableBarrierLeft() implements RRule {
    // The permeable Barrier on the left input is modeled as an identity
    // projection, exactly as in the accepted sibling rules
    // PushLeakproofJoinIntoPermeableBarrierRight and
    // PushLeakproofFiltersIntoPermeableBarrier: in CockroachDB the Barrier is
    // a pure flow-control point with no effect on the row bag, so its only
    // bag-semantic behavior is identity. The rule's preconditions (the
    // LeakproofPermeable flag via If $flag, IsLeakproof $right, and
    // HasAllLeakProofFilters $on) gate when the planner may fire the rewrite:
    // they express reorderability under possible evaluation failure, a
    // partiality property with no bag-semantic counterpart. No data-semantic
    // precondition is dropped — the verifiable content of the rule is that
    // hoisting the Barrier from below the join to above it preserves the row
    // bag, which is what QED checks here.
    //
    // Left input beneath the barrier: two uninterpreted columns (l0, l1).
    static final RelRN left = RelRN.scanMany("L", Seq.of(
            new RelType.VarType("L0_Type", true),
            new RelType.VarType("L1_Type", true)));
    static final RelRN barrierLeft = left.project(left.fields());

    // Leakproof right input: two uninterpreted columns (r0, r1).
    static final RelRN right = RelRN.scanMany("R", Seq.of(
            new RelType.VarType("R0_Type", true),
            new RelType.VarType("R1_Type", true)));

    // Shared uninterpreted ON predicate C over the join's columns
    // (l0, l1, r0, r1) — stands in for the conjunction of the rule's
    // leakproof ON filters. Reusing the name "C" on both sides tells QED
    // the two occurrences are the same symbol.
    //
    // Before: InnerJoin(Barrier(L), R, C) — the join sits above the barrier.
    static final RelRN before =
            barrierLeft.join(JoinRelType.INNER, barrierLeft.joinPred("C", right), right);

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
