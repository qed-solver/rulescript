package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record AggregateExtractProject() implements RRule {
    static final RelRN scanA = RelRN.scan("A", "A_Type");
    static final RelRN scanB = RelRN.scan("B", "B_Type");
    static final RelRN scanC = RelRN.scan("C", "C_Type");
    static final RelRN base =
            scanA.join(JoinRelType.INNER, RexRN.trueLiteral(), scanB)
                 .join(JoinRelType.INNER, RexRN.trueLiteral(), scanC);

    static final RelRN beforeAgg = new RelRN.Aggregate(
            base,
            Seq.of(base.field(0)),
            Seq.of(base.field(1).aggCall("f")));

    static final RelRN project = base.project(Seq.of(
            base.field(0),
            base.field(1)));

    static final RelRN afterAgg = new RelRN.Aggregate(
            project,
            Seq.of(project.field(0)),
            Seq.of(project.field(1).aggCall("f")));

    @Override
    public RelRN before() {
        return beforeAgg;
    }

    @Override
    public RelRN after() {
        return afterAgg;
    }
}
