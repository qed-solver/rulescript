package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the 2-input UNION ALL instance with both inputs sharing one column type, whereas Calcite's rule applies to any set-op kind (UNION/INTERSECT/MINUS, ALL or DISTINCT) of any arity.
public record FilterSetOpTranspose() implements RRule {
    static final RelRN left = RelRN.scan("L", "T");
    static final RelRN right = RelRN.scan("R", "T");

    static final RexRN pLeft = left.pred("P");
    static final RexRN pRight = right.pred("P");

    @Override
    public RelRN before() {
        final RelRN unionAll = left.union(true, right);
        return unionAll.filter(unionAll.pred("P"));
    }

    @Override
    public RelRN after() {
        return left.filter(pLeft).union(true, right.filter(pRight));
    }
}