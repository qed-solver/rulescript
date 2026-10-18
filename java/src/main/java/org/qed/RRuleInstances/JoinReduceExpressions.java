package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the join kind is fixed to INNER (the rule applies to all join kinds, but QED models the join kind concretely, not as an uninterpreted symbol) and the join condition is a conjunction of an uninterpreted predicate with the constant FALSE
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
