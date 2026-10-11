package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — swaps two distinct ON-filter items in an INNER join (minimal 2-element unsorted list) rather than sorting an arbitrary-length filter list with the backend's SortFilters
public record SortFiltersInJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");
    static final RexRN a = left.joinPred("a", right);
    static final RexRN b = left.joinPred("b", right);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, RexRN.and(a, b), right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER, RexRN.and(b, a), right);
    }
}