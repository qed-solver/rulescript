// SCOPE: PARTIAL — assumes the two OR operands are each a binary conjunction sharing exactly one common conjunct, i.e. the (A AND B) OR (A AND C) => A AND (B OR C) instance
package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import kala.collection.Seq;

public record ExtractRedundantConjunct() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN a = source.pred("a");
    static final RexRN b = source.pred("b");
    static final RexRN c = source.pred("c");

    @Override
    public RelRN before() {
        return source.filter(new RexRN.Or(Seq.of(RexRN.and(a, b), RexRN.and(a, c))));
    }

    @Override
    public RelRN after() {
        return source.filter(RexRN.and(a, new RexRN.Or(Seq.of(b, c))));
    }
}