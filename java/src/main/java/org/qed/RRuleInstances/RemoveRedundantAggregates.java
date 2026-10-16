package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — the upper aggregate has no aggregate calls and its single group key is the lower aggregate's first group key, the lower's second group key and its aggregate call are unreferenced by the upper, and the input is a one-column scan.
public record RemoveRedundantAggregates() implements RRule {
    static final RelRN source = RelRN.scan("S", "S_Type");
    static final RexRN k1 = source.groupBy("k1");
    static final RexRN k2 = source.groupBy("k2");

    // Lower aggregate: groups the source by k1, k2 and computes one
    // aggregate call v, which the upper aggregate never references.
    static final RelRN lower =
            new RelRN.Aggregate(
                    source,
                    Seq.of(k1, k2),
                    Seq.of(source.field(0).aggCall("v")));

    // Before: the upper aggregate has no aggregate calls and references
    // only the lower's first output column (a deterministic non-agg
    // group-key output), so all of the lower's work is redundant.
    static final RelRN beforeTop =
            new RelRN.Aggregate(lower, Seq.of(lower.field(0)), Seq.empty());

    // After: the rule replaces the lower aggregate with a projection of
    // the keys the upper references, over the lower's child.
    static final RelRN projected = source.project(Seq.of(k1));
    static final RelRN afterTop =
            new RelRN.Aggregate(projected, Seq.of(projected.field(0)), Seq.empty());

    @Override
    public RelRN before() {
        return beforeTop;
    }

    @Override
    public RelRN after() {
        return afterTop;
    }
}