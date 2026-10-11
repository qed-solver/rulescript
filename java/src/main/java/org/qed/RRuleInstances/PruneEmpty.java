package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
public record PruneEmpty() implements RRule {
    static final RelRN empty = RelRN.scan("T", "T_Type").empty();

    @Override
    public RelRN before() {
        return empty.filter("p");
    }

    @Override
    public RelRN after() {
        return empty;
    }
}
