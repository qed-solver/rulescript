package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record AggregateProjectMerge() implements RRule {
    static final RelRN scanA = RelRN.scan("A", "A_Type");
    static final RelRN scanB = RelRN.scan("B", "B_Type");
    static final RelRN base =
            scanA.join(JoinRelType.INNER, RexRN.trueLiteral(), scanB);
    static final RelRN project = base.project(Seq.of(
            base.field(1),
            base.field(0)));
    static final RelRN beforeAgg = new RelRN.Aggregate(
            project,
            Seq.of(project.field(0)),
            Seq.of(project.field(0).aggCall("f")));
    static final RelRN afterAgg = new RelRN.Aggregate(
            base,
            Seq.of(base.field(1)),
            Seq.of(base.field(1).aggCall("f")));

    @Override
    public RelRN before() {
        return beforeAgg;
    }

    @Override
    public RelRN after() {
        return afterAgg;
    }
}
