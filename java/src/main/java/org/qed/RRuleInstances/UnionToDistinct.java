package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;

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
