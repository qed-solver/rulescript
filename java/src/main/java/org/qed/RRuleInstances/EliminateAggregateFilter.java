package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the True-literal arm, i.e. every aggregate call in the node has FILTER (WHERE true), which is equivalent to filtering the aggregate input by the constant true; the False-literal arm (replacement by the empty/identity aggregate value) is out of reach for uninterpreted aggregates.
public record EliminateAggregateFilter() implements RRule {
    // Two-column input: col 0 = key (group key), col 1 = value (aggregate operand).
    static final RelRN input = RelRN.scanMany("Source", Seq.of(
            RexRN.varType("Key_Type", true),
            RexRN.varType("Value_Type", true)));

    static final RexRN key = input.field(0);
    // One non-distinct uninterpreted aggregate call over the value column.
    static final RelRN.AggCall agg = input.field(1).aggCall("f");
    static final Seq<RexRN> groupSet = Seq.of(key);

    // Before: Aggregate(Filter(true, Source), GROUP BY key, f(value)) — the node's
    // FILTER (WHERE true) clause, modeled as a trivial filter over the aggregate input.
    static final RelRN before = new RelRN.Aggregate(input.filter(RexRN.trueLiteral()), groupSet, Seq.of(agg));

    // After: Aggregate(Source, GROUP BY key, f(value)) — the useless true-filter eliminated.
    static final RelRN after = new RelRN.Aggregate(input, groupSet, Seq.of(agg));

    @Override
    public RelRN before() {
        return before;
    }

    @Override
    public RelRN after() {
        return after;
    }
}
