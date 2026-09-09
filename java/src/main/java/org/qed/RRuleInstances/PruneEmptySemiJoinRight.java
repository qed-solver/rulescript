package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;

public record PruneEmptySemiJoinRight() implements RRule {
    static final RelRN left = RelRN.scan("Left", "Left_Type");
    static final RelRN rightType = RelRN.scan("RightType", "Right_Type");

    @Override
    public RelRN before() {
        return left.join(JoinRelType.SEMI, left.joinPred("condition", rightType), rightType.empty());
    }

    @Override
    public RelRN after() {
        return left.empty();
    }
}
