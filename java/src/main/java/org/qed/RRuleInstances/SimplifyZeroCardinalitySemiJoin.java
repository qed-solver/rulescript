package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// SCOPE: PARTIAL — the HasZeroRows precondition is encoded as a literal .empty() relation (a statically known-empty shape) and the join filter is fixed to an uninterpreted on-condition
public record SimplifyZeroCardinalitySemiJoin() implements RRule {
    static final RelRN left = RelRN.scan("Left", "Left_Type");
    static final RelRN right = RelRN.scan("Right", "Right_Type").empty();
    static final RexRN on = left.joinPred("on", right);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.SEMI, on, right);
    }

    @Override
    public RelRN after() {
        return left.empty();
    }
}
