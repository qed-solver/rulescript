package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

public record UnionMerge() implements RRule {
    static final RelRN a = RelRN.scan("A", "T");
    static final RelRN b = RelRN.scan("B", "T");
    static final RelRN c = RelRN.scan("C", "T");
    static final RelRN d = RelRN.scan("D", "T");

    @Override
    public RelRN before() {
        return a.union(true, b.union(true, c), d);
    }

    @Override
    public RelRN after() {
        return a.union(true, b, c, d);
    }
}