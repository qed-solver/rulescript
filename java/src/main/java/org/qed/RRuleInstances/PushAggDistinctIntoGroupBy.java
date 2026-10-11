package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RuleBuilder;
import org.qed.RexRN;

// SCOPE: PARTIAL — the GroupBy/ScalarGroupBy has exactly one aggregation, a single-argument DISTINCT call, with two grouping columns and no non-distinct aggregations, whereas the source rule matches any single aggregate function with any input arguments over any grouping set.
public record PushAggDistinctIntoGroupBy() implements RRule {
    static final RelRN source = RelRN.scan("S", "S_Type");
    static final RexRN k1 = source.groupBy("k1");
    static final RexRN k2 = source.groupBy("k2");
    static final RexRN x = source.groupBy("x");
    static final RelType fType = new RelType.VarType("f_type", true);

    static final RelRN.AggCall fDistinct = new RelRN.AggCall(
            "f", RuleBuilder.create().genericAggregateOp("f", fType), true, fType, Seq.of(x));
    static final RelRN beforeAgg = new RelRN.Aggregate(source, Seq.of(k1, k2), Seq.of(fDistinct));

    // DistinctOn: deduplicate on grouping cols union distinct-arg cols (empty agg list projects to the group keys).
    static final RelRN inner = new RelRN.Aggregate(source, Seq.of(k1, k2, x), Seq.empty());
    static final RelRN.AggCall fPlain = new RelRN.AggCall(
            "f", RuleBuilder.create().genericAggregateOp("f", fType), false, fType, Seq.of(inner.field(2)));
    static final RelRN afterAgg = new RelRN.Aggregate(inner, Seq.of(inner.field(0), inner.field(1)), Seq.of(fPlain));

    @Override
    public RelRN before() {
        return beforeAgg;
    }

    @Override
    public RelRN after() {
        return afterAgg;
    }
}
