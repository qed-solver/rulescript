package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the window is modeled as a group-by/aggregate with a single partition key and a single aggregate call, the input is an arbitrary two-column relation, and the filter is a conjunction of one conjunct over the partition key only (the pushed one) and one conjunct over the window output row (the remaining one)
public record FilterWindowTranspose() implements RRule {
    static final RelRN base = RelRN.scanMany("B", Seq.of(
            RexRN.varType("K_Type", true),
            RexRN.varType("V_Type", true)));
    static final RexRN k = base.field(0);
    static final RexRN v = base.field(1);

    static final RelRN agg = new RelRN.Aggregate(base, Seq.of(k), Seq.of(v.aggCall("f")));

    static final RexRN predP = k.pred("P");

    static final RexRN predQ = new RexRN.Pred(
            RuleBuilder.create().genericPredicateOp("Q", true),
            Seq.of(agg.field(0), agg.field(1)));

    @Override
    public RelRN before() {
        return agg.filter(RexRN.and(predP, predQ));
    }

    @Override
    public RelRN after() {
        return new RelRN.Aggregate(base.filter(predP), Seq.of(k), Seq.of(v.aggCall("f")))
                .filter(predQ);
    }
}
