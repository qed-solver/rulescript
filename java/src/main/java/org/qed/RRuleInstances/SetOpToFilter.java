package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
public record SetOpToFilter() implements RRule {
    static final RelRN source = RelRN.scan("Source", "Source_Type");
    static final RexRN p1 = source.pred("P1");
    static final RexRN p2 = source.pred("P2");

    static final RelRN f1 = source.filter(p1);
    static final RelRN f2 = source.filter(p2);

    @Override
    public RelRN before() {
        return f1.union(false, f2);
    }

    @Override
    public RelRN after() {
        RelRN filtered = source.filter(new RexRN.Or(Seq.of(p1, p2)));
        return new RelRN.Aggregate(filtered, Seq.of(filtered.field(0)), Seq.empty());
    }
}
