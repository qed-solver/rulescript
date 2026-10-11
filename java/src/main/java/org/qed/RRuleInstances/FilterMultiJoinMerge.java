package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the MultiJoin is an inner-only (INNER pairwise joins, no semi/anti or full-outer factors) three-factor join tree carrying a non-null post-join filter over the full row, and the above filter is merged as an AND into that post-join filter rather than being classified/distributed per conjunct across the inputs
public record FilterMultiJoinMerge() implements RRule {
    static final RelRN a = RelRN.scan("A", "A_Type");
    static final RelRN b = RelRN.scan("B", "B_Type");
    static final RelRN c = RelRN.scan("C", "C_Type");

    static final RelRN ab = a.join(JoinRelType.INNER, RexRN.trueLiteral(), b);
    static final RexRN postJoinFilter = ab.joinPred("post_join_filter", c);

    static final RelRN abc = ab.join(JoinRelType.INNER, postJoinFilter, c);
    static final RexRN filterCond = abc.pred("filter_cond");

    @Override
    public RelRN before() {
        return abc.filter(filterCond);
    }

    @Override
    public RelRN after() {
        return ab.join(JoinRelType.INNER, RexRN.and(postJoinFilter, filterCond), c);
    }
}
