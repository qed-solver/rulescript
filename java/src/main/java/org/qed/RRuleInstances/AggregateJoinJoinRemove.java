package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RuleBuilder;
import org.qed.RexRN;

public record AggregateJoinJoinRemove() implements RRule {
    static final RelRN a = RelRN.scan("A", "A_Type");
    static final RelRN b = RelRN.scan("B", "B_Type");
    static final RelRN c = RelRN.scan("C", "C_Type");

    static final RelRN bottom =
            a.join(JoinRelType.LEFT, a.joinPred("bottom_cond", b), b);

    static final SqlOperator topOp = RuleBuilder.create().genericPredicateOp("top_cond", true);
    static final RexRN topCondBefore =
            new RexRN.Pred(topOp, Seq.of(bottom.field(0), bottom.joinField(2, c)));
    static final RexRN topCondAfter =
            new RexRN.Pred(topOp, Seq.of(a.joinField(0, c), a.joinField(1, c)));

    static final RelRN beforeJoin = bottom.join(JoinRelType.LEFT, topCondBefore, c);
    static final RelRN beforeAgg = new RelRN.Aggregate(
            beforeJoin,
            Seq.of(beforeJoin.field(0), beforeJoin.field(2)),
            Seq.empty());

    static final RelRN afterJoin = a.join(JoinRelType.LEFT, topCondAfter, c);
    static final RelRN afterAgg = new RelRN.Aggregate(
            afterJoin,
            Seq.of(afterJoin.field(0), afterJoin.field(1)),
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
