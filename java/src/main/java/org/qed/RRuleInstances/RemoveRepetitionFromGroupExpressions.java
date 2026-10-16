package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — group keys are plain input columns with the first key duplicated as the third, and a single aggregate call.
public record RemoveRepetitionFromGroupExpressions() implements RRule {
    static final RelRN s = RelRN.scanMany("S", Seq.of(RexRN.varType("A_Type", true), RexRN.varType("B_Type", true), RexRN.varType("Y_Type", true)));
    static final RelRN s2 = s.project(Seq.of(s.field(0), s.field(1), s.field(0), s.field(2)));

    @Override
    public RelRN before() {
        RelRN agg = new RelRN.Aggregate(s2,
                Seq.of(s2.field(0), s2.field(1), s2.field(2)),
                Seq.of(new RelRN.AggCall("sum", false, RexRN.varType("Sum_Type", true), Seq.of(s2.field(3)))));
        return agg.project(Seq.of(agg.field(0), agg.field(1), agg.field(3)));
    }

    @Override
    public RelRN after() {
        return new RelRN.Aggregate(s,
                Seq.of(s.field(0), s.field(1)),
                Seq.of(new RelRN.AggCall("sum", false, RexRN.varType("Sum_Type", true), Seq.of(s.field(2)))));
    }
}
