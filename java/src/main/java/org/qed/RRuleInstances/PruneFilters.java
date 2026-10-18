// ORIGIN: Spark
// SCOPE: PARTIAL — only the constraint-pruning case where a conjunct of the outer filter is identical to a conjunct of the immediate child filter's predicate
package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

public record PruneFilters() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN shared = source.pred("shared");
    static final RexRN restOuter = source.pred("rest_outer");
    static final RexRN restInner = source.pred("rest_inner");

    @Override
    public RelRN before() {
        return source.filter(RexRN.and(shared, restInner))
                     .filter(RexRN.and(shared, restOuter));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.and(shared, restInner))
                     .filter(restOuter);
    }
}
