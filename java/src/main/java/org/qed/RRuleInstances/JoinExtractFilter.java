package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// SCOPE: FULL
public record JoinExtractFilter() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    static final RexRN joinCond = left.joinPred("join_cond", right);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, joinCond, right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER, RexRN.trueLiteral(), right).filter(joinCond);
    }
}