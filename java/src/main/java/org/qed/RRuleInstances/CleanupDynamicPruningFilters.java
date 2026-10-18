package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — the dynamic pruning filter is modeled as an uninterpreted probe-side predicate that is also conjunctively enforced by the enclosing inner join's condition, rather than as an IN(subquery) implied by key equality
public record CleanupDynamicPruningFilters() implements RRule {
    static final RelRN L = RelRN.scan("L", "L_Type");
    static final RelRN R = RelRN.scan("R", "R_Type");
    static final RexRN dpp = L.pred("dpp");
    static final RexRN extra = L.joinPred("extra", R);

    @Override
    public RelRN before() {
        return L.filter(dpp).join(JoinRelType.INNER, RexRN.and(dpp, extra), R);
    }

    @Override
    public RelRN after() {
        return L.join(JoinRelType.INNER, RexRN.and(dpp, extra), R);
    }
}