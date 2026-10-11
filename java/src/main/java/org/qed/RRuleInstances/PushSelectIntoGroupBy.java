package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — only the GroupBy branch of the source rule is modeled (not the DistinctOn alternative), over an uninterpreted two-column base relation with a single identity grouping column and one non-distinct aggregate call, with every filter conjunct referencing only the grouping column (the rule's ConstAgg-column case is unmodelable: QED's aggregates are uninterpreted, so it cannot see that a ConstAgg's output is per-group-constant).
public record PushSelectIntoGroupBy() implements RRule {
    // col 0 = x (grouping column), col 1 = y (aggregate input)
    static final RelRN base = RelRN.scanMany("Input",
            Seq.of(RexRN.varType("X_Type", false), RexRN.varType("Y_Type", false)));
    static final RexRN key = base.field(0);
    static final RexRN predP = key.pred("P");
    static final RexRN predR = key.pred("R");
    static final RelRN.AggCall f = base.field(1).aggCall("f");
    static final RelRN agg = new RelRN.Aggregate(base, Seq.of(key), Seq.of(f));
    static final RexRN predPUp = agg.field(0).pred("P");
    static final RexRN predRUp = agg.field(0).pred("R");

    @Override
    public RelRN before() {
        return agg.filter(RexRN.and(predPUp, predRUp));
    }

    @Override
    public RelRN after() {
        // All filter conjuncts are bound by the grouping columns, so
        // ExtractUnboundConditions is empty and the outer Select is vacuous.
        return new RelRN.Aggregate(base.filter(RexRN.and(predP, predR)), Seq.of(key), Seq.of(f));
    }
}
