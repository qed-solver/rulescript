package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record MultiJoinOptimizeBushy() implements RRule {
    static final RelRN A = RelRN.scan("A", "A_Type");
    static final RelRN B = RelRN.scan("B", "B_Type");
    static final RelRN C = RelRN.scan("C", "C_Type");
    static final RelRN D = RelRN.scan("D", "D_Type");

    static final SqlOperator F = RuleBuilder.create().genericPredicateOp("F", true);

    static final RelRN ab = A.join(JoinRelType.INNER, RexRN.trueLiteral(), B);
    static final RelRN abc = ab.join(JoinRelType.INNER, RexRN.trueLiteral(), C);
    static final RexRN beforeCond = new RexRN.Pred(F, Seq.of(
            abc.joinField(0, D),
            abc.joinField(1, D),
            abc.joinField(2, D),
            abc.joinField(3, D)));
    static final RelRN beforeJoin = abc.join(JoinRelType.INNER, beforeCond, D);

    static final RelRN cd = C.join(JoinRelType.INNER, RexRN.trueLiteral(), D);
    static final RexRN afterCond = new RexRN.Pred(F, Seq.of(
            ab.joinField(0, cd),
            ab.joinField(1, cd),
            ab.joinField(2, cd),
            ab.joinField(3, cd)));
    static final RelRN afterJoin = ab.join(JoinRelType.INNER, afterCond, cd);

    @Override
    public RelRN before() {
        return beforeJoin;
    }

    @Override
    public RelRN after() {
        return afterJoin;
    }
}
