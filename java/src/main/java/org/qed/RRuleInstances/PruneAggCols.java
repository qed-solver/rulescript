package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// SCOPE: PARTIAL — fixed 3-column scan (group key plus two aggregate arguments), one group key, and an outer project that selects only the group key and the retained single-arg aggregate f so the pruned single-arg aggregate g is never used.
public record PruneAggCols() implements RRule {
    static final RelRN base = RelRN.scanMany("S",
            Seq.of(RexRN.varType("K_Type", true), RexRN.varType("A_Type", true), RexRN.varType("B_Type", true)));

    static final RexRN key = base.field(0);
    static final RexRN a = base.field(1);
    static final RexRN b = base.field(2);

    // Retained aggregate f(a): identical uninterpreted aggregate (same name and result
    // type) on both sides, since the source rule requires the kept aggregation to be
    // unchanged.
    static final RelRN.AggCall f = new RelRN.AggCall("f", false,
            new RelType.VarType("F_Type", true), Seq.of(a));
    // Pruned aggregate g(b): present only in before(), never selected by the project.
    static final RelRN.AggCall g = new RelRN.AggCall("g", false,
            new RelType.VarType("G_Type", true), Seq.of(b));

    @Override
    public RelRN before() {
        RelRN agg = new RelRN.Aggregate(base, Seq.of(key), Seq.of(f, g));
        // Outer project picks the group key (field 0) and the retained aggregate f
        // (field 1); the pruned aggregate g (field 2) is never used.
        return agg.project(Seq.of(agg.field(0), agg.field(1)));
    }

    @Override
    public RelRN after() {
        RelRN agg = new RelRN.Aggregate(base, Seq.of(key), Seq.of(f));
        // Same outer projection shape: group key (field 0) and retained f (field 1).
        return agg.project(Seq.of(agg.field(0), agg.field(1)));
    }
}
