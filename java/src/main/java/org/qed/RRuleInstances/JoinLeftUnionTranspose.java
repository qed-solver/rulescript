package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;

public record JoinLeftUnionTranspose() implements RRule {
    static final RelRN leftA = RelRN.scan("LeftA", "Left_Type");
    static final RelRN leftB = RelRN.scan("LeftB", "Left_Type");
    static final RelRN right = RelRN.scan("Right", "Right_Type");

    @Override
    public RelRN before() {
        var union = leftA.union(true, leftB);
        return union.join(JoinRelType.INNER, union.joinPred("condition", right), right);
    }

    @Override
    public RelRN after() {
        var first = leftA.join(JoinRelType.INNER, leftA.joinPred("condition", right), right);
        var second = leftB.join(JoinRelType.INNER, leftB.joinPred("condition", right), right);
        return first.union(true, second);
    }
}
