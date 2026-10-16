package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — the Distinct-like node is a plain Distinct, the UNION ALL has exactly two inputs, and both inputs are the identical relation (Spark's rule dedups canonically-equal children — modulo alias-only Projects — over any number of inputs).
public record RemoveNoopUnion() implements RRule {
    static final RelRN a = RelRN.scan("A", "T");

    // Distinct(R) = group-by-all-columns with no aggregate calls (set/dedup semantics).
    static RelRN distinct(RelRN r) {
        return new RelRN.Aggregate(r, Seq.of(r.field(0)), Seq.empty());
    }

    @Override
    public RelRN before() {
        // Distinct(UNION ALL(A, A)): the two union children are canonically equal (one scan), so simplifyUnion collapses them.
        return distinct(a.union(true, a));
    }

    @Override
    public RelRN after() {
        // Distinct(A): the no-op union is removed, leaving the single child (u.children.head).
        return distinct(a);
    }
}
