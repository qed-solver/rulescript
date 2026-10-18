package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: CockroachDB
// SCOPE: PARTIAL — the scalar In predicate over an empty tuple is modeled as a relational semi-join against a zero-row relation, since QED has no scalar In predicate or list constant.
public record FoldInEmpty() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    // (Tuple []): the In's right side is the empty set — a zero-row relation.
    static final RelRN right = RelRN.scan("R", "R_Type").empty();

    @Override
    public RelRN before() {
        // x IN (empty): no right row can ever match x, so no left row
        // survives — exactly the relational semi-join of L with the empty right.
        // This holds even when x is NULL: an unknown value is not in an empty set.
        return left.join(JoinRelType.SEMI, "cond", right);
    }

    @Override
    public RelRN after() {
        // In(x, ()) folds to False, and Filter(False, L) is the empty
        // relation of L's schema.
        return left.empty();
    }
}
