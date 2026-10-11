package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// SCOPE: PARTIAL — fixed 4-column scan (one group key, two aggregate arguments, one unused column), one group key, two non-distinct single-argument aggregates, and the outer projection selects all aggregate output columns.
public record PruneGroupByCols() implements RRule {
    static final RelRN base = RelRN.scanMany("S",
            Seq.of(RexRN.varType("K_Type", true), RexRN.varType("A_Type", true),
                    RexRN.varType("B_Type", true), RexRN.varType("C_Type", true)));

    static final RexRN key = base.field(0);
    static final RexRN a = base.field(1);
    static final RexRN b = base.field(2);
    // Column c (base.field(3)) is never referenced by the group key or any aggregate
    // argument — this is exactly the column the rule prunes from the input.

    @Override
    public RelRN before() {
        // GroupBy over the full 4-column input: group key k, aggregations f(a), g(b).
        RelRN agg = new RelRN.Aggregate(base, Seq.of(key),
                Seq.of(new RelRN.AggCall("f", false, new RelType.VarType("F_Type", true), Seq.of(a)),
                      new RelRN.AggCall("g", false, new RelType.VarType("G_Type", true), Seq.of(b))));
        return agg.project(Seq.of(agg.field(0), agg.field(1), agg.field(2)));
    }

    @Override
    public RelRN after() {
        // (PruneCols $input $needed): keep only the needed columns k, a, b.
        RelRN pruned = base.project(Seq.of(base.field(0), base.field(1), base.field(2)));
        RexRN pKey = pruned.field(0);
        RexRN pA = pruned.field(1);
        RexRN pB = pruned.field(2);
        // Same uninterpreted aggregations f and g (same names/result types),
        // unchanged except that they now read the pruned input's columns.
        RelRN agg = new RelRN.Aggregate(pruned, Seq.of(pKey),
                Seq.of(new RelRN.AggCall("f", false, new RelType.VarType("F_Type", true), Seq.of(pA)),
                      new RelRN.AggCall("g", false, new RelType.VarType("G_Type", true), Seq.of(pB))));
        return agg.project(Seq.of(agg.field(0), agg.field(1), agg.field(2)));
    }
}
