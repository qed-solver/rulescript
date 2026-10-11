package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Calcite
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
