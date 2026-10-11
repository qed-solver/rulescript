// SCOPE: PARTIAL — assumes a filter list of three conjuncts in which exactly one predicate is duplicated alongside one independent predicate
package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record DeduplicateSelectFilters() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN p = source.pred("p");
    static final RexRN q = source.pred("q");

    @Override
    public RelRN before() {
        return source.filter(p).filter(q).filter(p);
    }

    @Override
    public RelRN after() {
        return source.filter(p).filter(q);
    }
}