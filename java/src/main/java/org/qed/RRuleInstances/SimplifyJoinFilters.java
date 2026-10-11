package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — only removes a True ON item, INNER join only
public record SimplifyJoinFilters() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");
    static final RexRN rest = left.joinPred("rest", right);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, RexRN.and(rest, RexRN.trueLiteral()), right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER, rest, right);
    }
}