package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Calcite
public record AggregateJoinRemove() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    static final RexRN joinCond = left.joinPred("join_cond", right);

    static final RelRN join = left.join(JoinRelType.LEFT, joinCond, right);

    static final RelRN beforeAgg = new RelRN.Aggregate(
            join,
            Seq.of(join.field(0)),
            Seq.empty());

    static final RelRN afterAgg = new RelRN.Aggregate(
            left,
            Seq.of(left.field(0)),
            Seq.empty());

    @Override
    public RelRN before() {
        return beforeAgg;
    }

    @Override
    public RelRN after() {
        return afterAgg;
    }
}
