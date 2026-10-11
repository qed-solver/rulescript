package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record JoinToHyperGraph() implements RRule {
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");

    static final SqlOperator pAB = RuleBuilder.create().genericPredicateOp("pAB", true);
    static final SqlOperator pAC = RuleBuilder.create().genericPredicateOp("pAC", true);
    static final SqlOperator pBC = RuleBuilder.create().genericPredicateOp("pBC", true);

    static final RexRN abCond = new RexRN.Pred(pAB, Seq.of(A.joinField(0, B), A.joinField(1, B)));
    static final RelRN hyper = A.join(JoinRelType.INNER, abCond, B);

    static final RexRN beforeCond = RexRN.and(
            new RexRN.Pred(pAC, Seq.of(hyper.joinField(0, C), hyper.joinField(2, C))),
            new RexRN.Pred(pBC, Seq.of(hyper.joinField(1, C), hyper.joinField(2, C))));
    static final RelRN before = hyper.join(JoinRelType.INNER, beforeCond, C);

    static final RexRN bcCond = new RexRN.Pred(pBC, Seq.of(B.joinField(0, C), B.joinField(1, C)));
    static final RelRN bc = B.join(JoinRelType.INNER, bcCond, C);

    static final RexRN afterCond = RexRN.and(
            new RexRN.Pred(pAB, Seq.of(A.joinField(0, bc), A.joinField(1, bc))),
            new RexRN.Pred(pAC, Seq.of(A.joinField(0, bc), A.joinField(2, bc))));
    static final RelRN after = A.join(JoinRelType.INNER, afterCond, bc);

    @Override
    public RelRN before() {
        return before;
    }

    @Override
    public RelRN after() {
        return after;
    }
}