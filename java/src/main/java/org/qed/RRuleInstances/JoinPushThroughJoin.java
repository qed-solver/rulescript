package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;


public record JoinPushThroughJoin() implements RRule {
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");

    static final SqlOperator SA = RuleBuilder.create().genericPredicateOp("SA", true);
    static final SqlOperator SB = RuleBuilder.create().genericPredicateOp("SB", true);
    static final SqlOperator ST = RuleBuilder.create().genericPredicateOp("ST", true);
    static final SqlOperator TC = RuleBuilder.create().genericPredicateOp("TC", true);

    static final RelRN abJoin = A.join(JoinRelType.INNER, RexRN.and(
            new RexRN.Pred(SA, Seq.of(A.joinField(0, B))),
            new RexRN.Pred(SB, Seq.of(A.joinField(1, B)))), B);

    static final RelRN beforeJoin = abJoin.join(JoinRelType.INNER, RexRN.and(
            new RexRN.Pred(ST, Seq.of(abJoin.joinField(0, C), abJoin.joinField(1, C))),
            new RexRN.Pred(TC, Seq.of(abJoin.joinField(2, C)))), C);

    static final RelRN acJoin = A.join(JoinRelType.INNER, RexRN.and(
            new RexRN.Pred(SA, Seq.of(A.joinField(0, C))),
            new RexRN.Pred(TC, Seq.of(A.joinField(1, C)))), C);

    static final RelRN afterJoin = acJoin.join(JoinRelType.INNER, RexRN.and(
            new RexRN.Pred(ST, Seq.of(acJoin.joinField(0, B), acJoin.joinField(2, B))),
            new RexRN.Pred(SB, Seq.of(acJoin.joinField(2, B)))), B);

    @Override
    public RelRN before() {
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        return afterJoin.project(Seq.of(
                afterJoin.field(0),
                afterJoin.field(2),
                afterJoin.field(1)));
    }
}
