package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — only the Filter-over-empty subrule of Calcite's PruneEmptyRule is encoded (Filter on an empty input collapses to the empty relation); the union/intersect/minus/join/correlate/sort/window variants are not covered.
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
