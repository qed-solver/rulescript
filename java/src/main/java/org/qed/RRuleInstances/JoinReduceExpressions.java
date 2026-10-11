package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
public record JoinReduceExpressions() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    static final RexRN c = left.joinPred("C", right);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, RexRN.and(c, RexRN.falseLiteral()), right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER, RexRN.falseLiteral(), right);
    }
}
