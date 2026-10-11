package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;

// SCOPE: PARTIAL — identity unordered DistinctOn (its output columns are exactly its grouping keys, with no aggregate outputs), and a single hoisted filter conjunct that references only the DistinctOn's output columns, so no FirstAgg synthesis is required.
public record HoistSelectAboveUnorderedDistinctOn() implements RRule {
    // The DistinctOn's input, whose output columns (the grouping keys) are k1, k2.
    static final RelType.VarType k1T = RexRN.varType("k1", false);
    static final RelType.VarType k2T = RexRN.varType("k2", false);
    static final RelRN base = RelRN.scanMany("Input", Seq.of(k1T, k2T));

    // Unordered DistinctOn with identity output and no aggregates, modeled as a
    // group-by whose keys are exactly its output columns (no aggregate calls).
    static final Seq<RexRN> keys = base.fields();
    static final RelRN distinctOn = new RelRN.Aggregate(base, keys, Seq.<RelRN.AggCall>empty());

    // The hoisted filter references only the DistinctOn's output (grouping-key)
    // columns, so it is constant within each group and may move above the
    // grouping: an unordered DistinctOn may pick any row of a group, so a
    // group-constant predicate holds for the picked row iff it holds for some
    // input row of the group.
    static final RexRN hoisted = base.pred("hoisted_filter");

    @Override
    public RelRN before() {
        // DistinctOn(Select(input, [hoisted]), keys, no aggs)
        return new RelRN.Aggregate(base.filter(hoisted), keys, Seq.<RelRN.AggCall>empty());
    }

    @Override
    public RelRN after() {
        // Select(DistinctOn(input, keys, no aggs), [hoisted])
        return distinctOn.filter(hoisted);
    }
}