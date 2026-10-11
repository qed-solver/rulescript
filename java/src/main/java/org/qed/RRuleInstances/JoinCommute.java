package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
public record JoinCommute() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");
    static final SqlOperator joinCond = RuleBuilder.create().genericPredicateOp("join_cond", true);

    static final RexRN beforeCond = new RexRN.Pred(joinCond, Seq.of(
            left.joinField(0, right),
            left.joinField(1, right)));

    static final RexRN lCol = right.joinField(1, left);
    static final RexRN rCol = right.joinField(0, left);
    static final RexRN afterCond = new RexRN.Pred(joinCond, Seq.of(lCol, rCol));

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, beforeCond, right);
    }

    @Override
    public RelRN after() {
        return right.join(JoinRelType.INNER, afterCond, left)
                .project(Seq.of(lCol, rCol));
    }
}
