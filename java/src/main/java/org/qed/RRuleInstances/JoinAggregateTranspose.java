package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record JoinAggregateTranspose() implements RRule {
    static final RelRN left = RelRN.scanMany("L", Seq.of(
            RexRN.varType("g_type", true), RexRN.varType("v_type", true)));
    static final RelRN right = RelRN.scan("R", RexRN.varType("R_Type", true), true);

    static final SqlOperator joinCond = RuleBuilder.create().genericPredicateOp("join_cond", true);

    static final RelRN beforeAgg = new RelRN.Aggregate(
            left,
            Seq.of(left.field(0)),
            Seq.of(left.field(1).aggCall("sum")));

    static final RelRN join = left.join(JoinRelType.INNER,
            new RexRN.Pred(joinCond, left.joinFields(right, 0, 2)), right);

    static final RelRN afterAgg = new RelRN.Aggregate(
            join,
            Seq.of(join.field(0), join.field(2)),
            Seq.of(join.field(1).aggCall("sum")));

    @Override
    public RelRN before() {
        return beforeAgg.join(JoinRelType.INNER,
                new RexRN.Pred(joinCond, beforeAgg.joinFields(right, 0, 2)), right);
    }

    @Override
    public RelRN after() {
        return afterAgg.project(Seq.of(
                afterAgg.field(0), afterAgg.field(2), afterAgg.field(1)));
    }
}
