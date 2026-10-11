package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — the 2-input set (distinct) INTERSECT instance with both inputs sharing one column type and one uninterpreted predicate, whereas Cockroach's rule applies to Union/Except/Intersect, all- or distinct-variants, of any arity.
public record PushFilterIntoSetOp() implements RRule {
    static final RelRN left = RelRN.scan("L", "T");
    static final RelRN right = RelRN.scan("R", "T");

    // Same predicate symbol P over both inputs (shared name = same uninterpreted symbol).
    static final RexRN pLeft = left.pred("P");
    static final RexRN pRight = right.pred("P");

    @Override
    public RelRN before() {
        final RelRN intersection = left.intersect(false, right);
        return intersection.filter(intersection.pred("P"));
    }

    @Override
    public RelRN after() {
        return left.filter(pLeft).intersect(false, right.filter(pRight));
    }
}
