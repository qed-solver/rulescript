package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only case 2 of the Spark rule (distinct key on child): the child is a unique scan on its single column and the DISTINCT aggregate takes exactly that column as its (sole) argument, so per-group argument values are already distinct; case 1 (Min/Max/BitAnd/BitOr/CollectSet/First/Last are duplicate-agnostic) is unprovable in QED, which models every aggregate, even one named "Min", as an uninterpreted function knowing only bag-equality of its input.
public record SparkEliminateDistinct() implements RRule {
    static final RelRN source = RelRN.scan("S", RexRN.varType("S_Type", false), true);
    static final RexRN uniqueCol = source.field(0);
    static final RexRN groupKey = source.groupBy("g");
    static final RelRN.AggCall distinctAgg = new RelRN.AggCall("f", true, RexRN.varType("F_Type", true), Seq.of(uniqueCol));
    static final RelRN.AggCall plainAgg   = new RelRN.AggCall("f", false, RexRN.varType("F_Type", true), Seq.of(uniqueCol));

    @Override
    public RelRN before() {
        return new RelRN.Aggregate(source, Seq.of(groupKey), Seq.of(distinctAgg));
    }

    @Override
    public RelRN after() {
        return new RelRN.Aggregate(source, Seq.of(groupKey), Seq.of(plainAgg));
    }
}
