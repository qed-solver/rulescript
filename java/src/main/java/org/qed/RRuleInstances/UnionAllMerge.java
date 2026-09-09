package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record UnionAllMerge() implements RRule {
    static final RelRN a = RelRN.scan("A", "Common_Type");
    static final RelRN b = RelRN.scan("B", "Common_Type");
    static final RelRN c = RelRN.scan("C", "Common_Type");

    @Override
    public RelRN before() {
        return a.union(true, b).union(true, c);
    }

    @Override
    public RelRN after() {
        return a.union(true, b, c);
    }
}
