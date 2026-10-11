package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;

// SCOPE: PARTIAL — the scalar NotIn predicate over an empty tuple is modeled as a relational anti-join against a zero-row relation, since QED has no scalar In/NotIn predicate or list constant.
public record FoldNotInEmpty() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    // (Tuple []): the NotIn's right side is the empty set — a zero-row relation.
    static final RelRN right = RelRN.scan("R", "R_Type").empty();

    @Override
    public RelRN before() {
        // x NOT IN (empty): no right row can ever match x, so every left row
        // survives — exactly the relational anti-join of L with the empty right.
        return left.join(JoinRelType.ANTI, "cond", right);
    }

    @Override
    public RelRN after() {
        // NotIn(x, ()) folds to True, and Filter(True, L) is just L.
        return left;
    }
}
