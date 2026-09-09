package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;

public record JoinRightUnionTranspose() implements RRule {
    static final RelRN left = RelRN.scan("Left", "Left_Type");
    static final RelRN rightA = RelRN.scan("RightA", "Right_Type");
    static final RelRN rightB = RelRN.scan("RightB", "Right_Type");

    @Override
    public RelRN before() {
        var union = rightA.union(true, rightB);
        return left.join(JoinRelType.INNER, left.joinPred("condition", union), union);
    }

    @Override
    public RelRN after() {
        var first = left.join(JoinRelType.INNER, left.joinPred("condition", rightA), rightA);
        var second = left.join(JoinRelType.INNER, left.joinPred("condition", rightB), rightB);
        return first.union(true, second);
    }
}
