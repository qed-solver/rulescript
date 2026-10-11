package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — Group.SIMPLE, a single identity group key over a two-column input, one non-distinct aggregate call, one pushed conjunct depending only on the group key, one remaining conjunct over the aggregate output.
public record FilterAggregateTranspose() implements RRule {
    static final RelRN scanA = RelRN.scan("A", "A_Type");
    static final RelRN scanB = RelRN.scan("B", "B_Type");
    static final RelRN base = scanA.join(JoinRelType.INNER, RexRN.trueLiteral(), scanB);
    static final RexRN key = base.field(0);
    static final RexRN predP = key.pred("P");
    static final RelRN.AggCall f = base.field(1).aggCall("f");
    static final RelRN agg = new RelRN.Aggregate(base, Seq.of(key), Seq.of(f));
    static final RexRN predPUp = agg.field(0).pred("P");
    static final RexRN predQ = RexRN.and(agg.field(0).pred("Qa"), agg.field(1).pred("Qb"));

    @Override
    public RelRN before() {
        return agg.filter(RexRN.and(predPUp, predQ));
    }

    @Override
    public RelRN after() {
        return new RelRN.Aggregate(base.filter(predP), Seq.of(key), Seq.of(f)).filter(predQ);
    }
}
