package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.apache.calcite.rel.core.JoinRelType;

// SCOPE: PARTIAL — non-Apply InnerJoin only; the ON condition and the Select's filter list are each abstracted as a single uninterpreted predicate
public record MergeSelectInnerJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");
    static final RexRN on = left.joinPred("on", right);
    static final RexRN above = left.joinPred("above_filter", right);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, on, right).filter(above);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER, RexRN.and(on, above), right);
    }
}
