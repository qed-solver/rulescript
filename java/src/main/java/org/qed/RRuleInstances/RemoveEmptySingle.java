package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — encodes the AGGREGATE variant of Calcite's RemoveEmptySingleRule: a non-grand-total (grouped) aggregate whose single input is empty collapses to the empty relation of the aggregate's output row type; the project/filter/calc/sort/window variants of the same rule are not covered here.
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
