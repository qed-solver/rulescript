package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — encodes only the unused-aggregate-call-removal half (single group key, project expressions as plain field references that keep every group key and drop one of three aggregate calls); the COALESCE(SUM(x), 0) to SUM0(x) conversion is not modeled because QED treats the two aggregate calls as distinct uninterpreted group-bag functions with no null-handling identity between them.
public record ProjectAggregateMerge() implements RRule {
    static final RelRN source = RelRN.scan("S", "S_Type");
    static final RexRN g = source.groupBy("g");
    static final RelRN.AggCall f1 = source.aggCall("f1");
    static final RelRN.AggCall f2 = source.aggCall("f2");
    static final RelRN.AggCall f3 = source.aggCall("f3");

    static final RelRN beforeAgg = new RelRN.Aggregate(source, Seq.of(g), Seq.of(f1, f2, f3));
    static final RelRN before = beforeAgg.project(Seq.of(
            beforeAgg.field(0),
            beforeAgg.field(1),
            beforeAgg.field(3)));

    static final RelRN reducedAgg = new RelRN.Aggregate(source, Seq.of(g), Seq.of(f1, f3));
    static final RelRN after = reducedAgg.project(Seq.of(
            reducedAgg.field(0),
            reducedAgg.field(1),
            reducedAgg.field(2)));

    @Override
    public RelRN before() {
        return before;
    }

    @Override
    public RelRN after() {
        return after;
    }
}
