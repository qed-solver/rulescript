package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Calcite
public record FilterJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    static final RexRN joinCond = left.joinPred("join_cond", right);
    static final RexRN aboveFilter = left.joinPred("above_filter", right);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, joinCond, right).filter(aboveFilter);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER, RexRN.and(joinCond, aboveFilter), right);
    }
}
