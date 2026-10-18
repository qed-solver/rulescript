package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — two-column projection over a single-column source with a two-conjunct filter where each conjunct references exactly one projected column; the pushed conjunct is rewritten onto its projection expression and pushed below the projection, the retained conjunct stays above (no projection split)
public record PushPredicateThroughNonJoin() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN p1 = source.proj("P1", "P1_Type");
    static final RexRN p2 = source.proj("P2", "P2_Type");
    static final RelRN projected = source.project(Seq.of(p1, p2));
    static final RexRN pushBefore = projected.field(0).pred("F");
    static final RexRN pushAfter = p1.pred("F");
    static final RexRN keep = projected.field(1).pred("G");

    @Override
    public RelRN before() {
        return projected.filter(RexRN.and(pushBefore, keep));
    }

    @Override
    public RelRN after() {
        return source.filter(pushAfter).project(Seq.of(p1, p2)).filter(keep);
    }
}