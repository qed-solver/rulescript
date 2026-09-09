package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record FilterIntersectTranspose() implements RRule {
    static final RelRN left = RelRN.scan("Left", "Common_Type");
    static final RelRN right = RelRN.scan("Right", "Common_Type");

    @Override
    public RelRN before() {
        var intersect = left.intersect(false, right);
        return intersect.filter(intersect.pred("condition"));
    }

    @Override
    public RelRN after() {
        return left.filter(left.pred("condition"))
                .intersect(false, right.filter(right.pred("condition")));
    }
}
