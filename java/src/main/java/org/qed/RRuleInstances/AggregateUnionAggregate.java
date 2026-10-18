package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the two union inputs are one-column relations of the same type (arbitrary-arity versions are not modeled) and the inner dedup is fixed to the second union input; both aggregates are group-by-all-columns with no aggregate calls, matching Calcite's isSimple / no-agg-call requirement.
public record AggregateUnionAggregate() implements RRule {
    static final RelRN l = RelRN.scan("L", "T");
    static final RelRN a = RelRN.scan("A", "T");

    static RelRN dedup(RelRN x) {
        return new RelRN.Aggregate(x, Seq.of(x.field(0)), Seq.empty());
    }

    @Override
    public RelRN before() {
        final RelRN union = a.union(true, dedup(l));
        return dedup(union);
    }

    @Override
    public RelRN after() {
        final RelRN union = a.union(true, l);
        return dedup(union);
    }
}
