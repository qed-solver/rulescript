package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — assumes the input is a base table whose single group column is declared unique (key constraint) and the aggregate has no aggregate calls; the splittable-aggregate-function half (e.g. MAX(x) -> x) is not modeled.
public record AggregateRemove() implements RRule {
    static final RelRN source = RelRN.scan("S", RexRN.varType("S_Type", true), true);

    @Override
    public RelRN before() {
        return new RelRN.Aggregate(source, Seq.of(source.field(0)), Seq.empty());
    }

    @Override
    public RelRN after() {
        return source;
    }
}
