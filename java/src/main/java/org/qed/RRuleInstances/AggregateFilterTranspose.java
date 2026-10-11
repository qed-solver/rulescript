package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record AggregateFilterTranspose() implements RRule {
    static final RelRN scanA = RelRN.scan("A", "A_Type");
    static final RelRN scanB = RelRN.scan("B", "B_Type");
    static final RelRN base = scanA.join(JoinRelType.INNER, RexRN.trueLiteral(), scanB);

    static final RexRN groupKey = base.field(0);
    static final RexRN pred = groupKey.pred("p");
    static final RelRN.AggCall f = base.field(1).aggCall("f");

    static final RelRN beforeAgg =
            new RelRN.Aggregate(base.filter(pred), Seq.of(groupKey), Seq.of(f));

    static final RelRN innerAgg =
            new RelRN.Aggregate(base, Seq.of(groupKey), Seq.of(f));
    static final RelRN afterAgg = innerAgg.filter(innerAgg.field(0).pred("p"));

    @Override
    public RelRN before() {
        return beforeAgg;
    }

    @Override
    public RelRN after() {
        return afterAgg;
    }
}
