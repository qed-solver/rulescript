package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the union has exactly two inputs (Calcite's rule fires on any number of inputs); both inputs are one-column relations of the same type, as required by Calcite's union type-checking.
public record UnionToDistinct() implements RRule {
    static final RelRN a = RelRN.scan("A", "T");
    static final RelRN b = RelRN.scan("B", "T");

    static final RelRN unionAll = a.union(true, b);

    @Override
    public RelRN before() {
        return a.union(false, b);
    }

    @Override
    public RelRN after() {
        return new RelRN.Aggregate(unionAll, Seq.of(unionAll.field(0)), Seq.empty());
    }
}
