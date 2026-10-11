package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
public record IntersectReorder() implements RRule {
    static final RelRN a = RelRN.scan("A", "T");
    static final RelRN b = RelRN.scan("B", "T");
    static final RelRN c = RelRN.scan("C", "T");

    @Override
    public RelRN before() {
        return a.intersect(false, b, c);
    }

    @Override
    public RelRN after() {
        return c.intersect(false, a, b);
    }
}
