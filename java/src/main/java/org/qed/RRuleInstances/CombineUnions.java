package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — fixed-shape instance of the flatten: a Distinct over a 3-input UNION ALL whose middle input is a nested 2-input Distinct(UNION ALL), flattened to a single 4-branch union with the redundant inner Distinct removed (Spark's rule handles arbitrary nesting depth/arity and also collapses nested projects and pushes projections through the union).
public record CombineUnions() implements RRule {
    static final RelRN a = RelRN.scan("A", "T");
    static final RelRN b = RelRN.scan("B", "T");
    static final RelRN c = RelRN.scan("C", "T");
    static final RelRN d = RelRN.scan("D", "T");

    // Distinct(R) = group-by-all-columns with no aggregate calls (set/dedup semantics).
    static RelRN distinct(RelRN r) {
        return new RelRN.Aggregate(r, r.fields(), Seq.empty());
    }

    @Override
    public RelRN before() {
        // Distinct(UNION ALL(A, Distinct(UNION ALL(B, C)), D))
        return distinct(a.union(true, distinct(b.union(true, c)), d));
    }

    @Override
    public RelRN after() {
        // Distinct(UNION ALL(A, B, C, D)) — nested union flattened, inner distinct removed.
        return distinct(a.union(true, b, c, d));
    }
}
