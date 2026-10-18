package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the aggregate's input is a two-column relation (two one-column scans inner-joined with a true condition), the project below the aggregate is the pure column swap, and the aggregate has one group key and one aggregate call, both over the same plain field reference
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
