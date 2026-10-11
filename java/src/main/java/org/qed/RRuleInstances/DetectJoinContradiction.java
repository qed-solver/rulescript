package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — INNER join only
public record DetectJoinContradiction() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    // The remaining, uninterpreted filter items surrounding the contradiction,
    // over the (L, R) join row: stands for the conjunction of all other
    // FiltersItems in the ON list.
    static final RexRN rest = left.joinPred("rest", right);

    @Override
    public RelRN before() {
        // InnerJoin(L, R, False AND rest) — the ON filter contains a
        // contradiction (literal False) among its items.
        return left.join(JoinRelType.INNER, RexRN.and(RexRN.falseLiteral(), rest), right);
    }

    @Override
    public RelRN after() {
        // InnerJoin(L, R, False) — the whole condition collapses to False.
        return left.join(JoinRelType.INNER, RexRN.falseLiteral(), right);
    }
}
