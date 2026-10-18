package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the top aggregate has no aggregate calls, the bottom aggregate has exactly one aggregate call (which the merged aggregate drops), the top's single group key is exactly the bottom's first group key, the bottom has exactly two group keys, and the input is a one-column scan.
public record AggregateMerge() implements RRule {
    static final RelRN source = RelRN.scan("S", "S_Type");

    static final RelRN bottom =
            new RelRN.Aggregate(
                    source,
                    Seq.of(source.groupBy("k1"), source.groupBy("k2")),
                    Seq.of(source.field(0).aggCall("v")));

    static final RelRN top =
            new RelRN.Aggregate(bottom, Seq.of(bottom.field(0)), Seq.empty());

    static final RelRN merged =
            new RelRN.Aggregate(source, Seq.of(source.groupBy("k1")), Seq.empty());

    @Override
    public RelRN before() {
        return top;
    }

    @Override
    public RelRN after() {
        return merged;
    }
}
