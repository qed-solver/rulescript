package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record FilterMinusTranspose() implements RRule {
    static final RelRN left = RelRN.scan("Left", "Common_Type");
    static final RelRN right = RelRN.scan("Right", "Common_Type");

    @Override
    public RelRN before() {
        var minus = left.minus(false, right);
        return minus.filter(minus.pred("condition"));
    }

    @Override
    public RelRN after() {
        return left.filter(left.pred("condition"))
                .minus(false, right.filter(right.pred("condition")));
    }
}
