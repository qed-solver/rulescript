package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;

public record JoinLeftUnionTransposeSemi() implements RRule {
    static final RelRN leftA = RelRN.scan("LeftA", "Left_Type");
    static final RelRN leftB = RelRN.scan("LeftB", "Left_Type");
    static final RelRN right = RelRN.scan("Right", "Right_Type");

    @Override public RelRN before() {
        var union = leftA.union(true, leftB);
        return union.join(JoinRelType.SEMI, union.joinPred("condition", right), right);
    }

    @Override public RelRN after() {
        return leftA.join(JoinRelType.SEMI, leftA.joinPred("condition", right), right)
                .union(true,
                    leftB.join(JoinRelType.SEMI, leftB.joinPred("condition", right), right));
    }
}
