package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record FilterUnionAllTranspose() implements RRule {
    static final RelRN left = RelRN.scan("Left", "Common_Type");
    static final RelRN right = RelRN.scan("Right", "Common_Type");

    @Override
    public RelRN before() {
        var union = left.union(true, right);
        return union.filter(union.pred("condition"));
    }

    @Override
    public RelRN after() {
        return left.filter(left.pred("condition"))
                .union(true, right.filter(right.pred("condition")));
    }
}
