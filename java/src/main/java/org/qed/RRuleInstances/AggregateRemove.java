package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

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
