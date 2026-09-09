package org.qed.RRuleInstances;

import org.qed.RelRN;
import org.qed.RRule;

public record UnionEliminator() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");

    @Override
    public RelRN before() {
        return source.union(true);
    }

    @Override
    public RelRN after() {
        return source;
    }
}
