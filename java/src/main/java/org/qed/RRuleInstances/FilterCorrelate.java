package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

import kala.collection.Seq;

public record FilterCorrelate() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    static final SqlOperator leftOp = RuleBuilder.create().genericPredicateOp("p_left", true);
    static final SqlOperator rightOp = RuleBuilder.create().genericPredicateOp("p_right", true);
    static final SqlOperator bothOp = RuleBuilder.create().genericPredicateOp("p_both", true);

    static final RexRN pLeft = new RexRN.Pred(leftOp, Seq.of(left.field(0)));
    static final RexRN pRight = new RexRN.Pred(rightOp, Seq.of(right.field(0)));

    static final RexRN pLeftJoin = new RexRN.Pred(leftOp, Seq.of(left.joinField(0, right)));
    static final RexRN pRightJoin = new RexRN.Pred(rightOp, Seq.of(left.joinField(1, right)));
    static final RexRN pBoth = left.joinPred(bothOp, right);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, RexRN.trueLiteral(), right)
                .filter(RexRN.and(pLeftJoin, pRightJoin, pBoth));
    }

    @Override
    public RelRN after() {
        return left.filter(pLeft)
                .join(JoinRelType.INNER, RexRN.trueLiteral(), right.filter(pRight))
                .filter(pBoth);
    }
}
