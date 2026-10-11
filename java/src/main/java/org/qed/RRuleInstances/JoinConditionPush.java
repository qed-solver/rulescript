package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record JoinConditionPush() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    static final SqlOperator leftOp = RuleBuilder.create().genericPredicateOp("left_conj", true);
    static final SqlOperator rightOp = RuleBuilder.create().genericPredicateOp("right_conj", true);
    static final SqlOperator crossOp = RuleBuilder.create().genericPredicateOp("cross_conj", true);

    static final RexRN lJoinRef = left.joinField(0, right);
    static final RexRN rJoinRef = left.joinField(1, right);

    static final RexRN leftJoinCond = new RexRN.Pred(leftOp, Seq.of(lJoinRef));
    static final RexRN rightJoinCond = new RexRN.Pred(rightOp, Seq.of(rJoinRef));
    static final RexRN crossCond = new RexRN.Pred(crossOp, Seq.of(lJoinRef, rJoinRef));

    static final RexRN leftRowCond = left.field(0).pred(leftOp);
    static final RexRN rightRowCond = right.field(0).pred(rightOp);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, RexRN.and(leftJoinCond, rightJoinCond, crossCond), right);
    }

    @Override
    public RelRN after() {
        return left.filter(leftRowCond)
                .join(JoinRelType.INNER, crossCond, right.filter(rightRowCond));
    }
}
