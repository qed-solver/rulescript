package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RuleBuilder;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — all distinct aggregate calls share the same single argument, no non-distinct calls, no FILTER/GROUPING SETS/WITHIN GROUP
public record AggregateExpandDistinctAggregates() implements RRule {
    static final RelRN source = RelRN.scan("S", "S_Type");
    static final RexRN k = source.groupBy("k");
    static final RexRN x = source.groupBy("x");
    static final RelType fType = new RelType.VarType("f_type", true);
    static final RelType gType = new RelType.VarType("g_type", true);

    static final RelRN.AggCall fDistinct = new RelRN.AggCall(
            "f", RuleBuilder.create().genericAggregateOp("f", fType), true, fType, Seq.of(x));
    static final RelRN.AggCall gDistinct = new RelRN.AggCall(
            "g", RuleBuilder.create().genericAggregateOp("g", gType), true, gType, Seq.of(x));
    static final RelRN beforeAgg = new RelRN.Aggregate(source, Seq.of(k), Seq.of(fDistinct, gDistinct));

    static final RelRN inner = new RelRN.Aggregate(source, Seq.of(k, x), Seq.empty());
    static final RelRN.AggCall fPlain = new RelRN.AggCall(
            "f", RuleBuilder.create().genericAggregateOp("f", fType), false, fType, Seq.of(inner.field(1)));
    static final RelRN.AggCall gPlain = new RelRN.AggCall(
            "g", RuleBuilder.create().genericAggregateOp("g", gType), false, gType, Seq.of(inner.field(1)));
    static final RelRN afterAgg = new RelRN.Aggregate(inner, Seq.of(inner.field(0)), Seq.of(fPlain, gPlain));

    @Override
    public RelRN before() {
        return beforeAgg;
    }

    @Override
    public RelRN after() {
        return afterAgg;
    }
}
