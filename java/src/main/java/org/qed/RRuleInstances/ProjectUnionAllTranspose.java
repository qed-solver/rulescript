package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record ProjectUnionAllTranspose() implements RRule {
    static final RelRN left = RelRN.scan("Left", "Input_Type");
    static final RelRN right = RelRN.scan("Right", "Input_Type");

    @Override
    public RelRN before() {
        var union = left.union(true, right);
        return union.project(union.proj("projection", "Output_Type"));
    }

    @Override
    public RelRN after() {
        return left.project(left.proj("projection", "Output_Type"))
                .union(true, right.project(right.proj("projection", "Output_Type")));
    }
}
