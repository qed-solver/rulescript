package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
public record RemoveEmptySingle() implements RRule {
    static final RelRN source = RelRN.scan("S", "S_Type");
    static final RelRN empty = source.empty();

    static final RelRN beforeAgg = new RelRN.Aggregate(
            empty,
            Seq.of(empty.field(0)),
            Seq.of(empty.field(0).aggCall("f")));

    @Override
    public RelRN before() {
        return beforeAgg;
    }

    @Override
    public RelRN after() {
        return beforeAgg.empty();
    }
}
