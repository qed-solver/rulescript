package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — each conjunct is a two-disjunct disjunction that shares exactly one common factor, with one residual disjunct per side
public record BooleanSimplification() implements RRule {

    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN A = source.pred("A");
    static final RexRN B = source.pred("B");
    static final RexRN C = source.pred("C");

    @Override
    public RelRN before() {
        return source.filter(RexRN.and(new RexRN.Or(Seq.of(A, B)), new RexRN.Or(Seq.of(A, C))));
    }

    @Override
    public RelRN after() {
        return source.filter(new RexRN.Or(Seq.of(A, RexRN.and(B, C))));
    }
}
