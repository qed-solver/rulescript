package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — the aggregation argument is the input scan's unique key, which is also the (single-column) grouping key, so each group holds at most one row and AggDistinct is a per-group no-op; the general CanRemoveAggDistinctForKeys condition (grouping cols functionally determine the agg arg over any input) is not expressible because QED only models scan uniqueness as a functional dependency.
public record EliminateAggDistinctForKeys() implements RRule {
    static final RelRN source = RelRN.scan("S", RexRN.varType("S_Type", false), true);
    static final RexRN key = source.field(0);
    static final RelRN.AggCall distinctAgg = new RelRN.AggCall("f", true, RexRN.varType("F_Type", true), Seq.of(key));
    static final RelRN.AggCall plainAgg   = new RelRN.AggCall("f", false, RexRN.varType("F_Type", true), Seq.of(key));

    @Override
    public RelRN before() {
        return new RelRN.Aggregate(source, Seq.of(key), Seq.of(distinctAgg));
    }

    @Override
    public RelRN after() {
        return new RelRN.Aggregate(source, Seq.of(key), Seq.of(plainAgg));
    }
}
