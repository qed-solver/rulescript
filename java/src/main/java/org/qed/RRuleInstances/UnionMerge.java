package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — only the UNION ALL instance with the nested union in the second input position; Calcite's rule also covers UNION DISTINCT, INTERSECT, and MINUS.
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