package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;

// SCOPE: PARTIAL — the outer grouping operator has no aggregate calls and its group key is exactly the inner grouping operator's group key (trivial identity functional dependency), so the fold only drops the inner's single aggregate call; the aggregate-merging algebra and non-trivial functional dependencies of the full rule are not modeled.
public record FoldGroupingOperators() implements RRule {
    static final RelRN source = RelRN.scan("S", "S_Type");

    // Inner grouping operator: group the source by one uninterpreted projection
    // k1 and compute one aggregate call v on the source's column.
    static final RelRN inner =
            new RelRN.Aggregate(
                    source,
                    Seq.of(source.groupBy("k1")),
                    Seq.of(source.field(0).aggCall("v")));

    // Before: the outer grouping operator groups the inner by its group key
    // (inner's first output column = k1) with no aggregate calls, so it only
    // re-groups the inner's output by k1 and drops the inner's v column.
    static final RelRN outer =
            new RelRN.Aggregate(inner, Seq.of(inner.field(0)), Seq.empty());

    // After: folding the two grouping operators into one, per MergeAggs with no
    // outer aggregate calls, which drops the inner's v call and keeps the group
    // key k1 — a single aggregate over the source grouped by k1 with no calls.
    static final RelRN folded =
            new RelRN.Aggregate(source, Seq.of(source.groupBy("k1")), Seq.empty());

    @Override
    public RelRN before() {
        return outer;
    }

    @Override
    public RelRN after() {
        return folded;
    }
}
