package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — no grouping, and a single COUNT(*) WITHIN DISTINCT (x) call (which the rule documents as identical to COUNT(DISTINCT x)) rewritten to its dedup-then-count expansion
public record AggregateExpandWithinDistinct() implements RRule {
    static final RelRN child = RelRN.scanMany("Child", Seq.of(
            new RelType.VarType("X_type", true)));
    static final RexRN x = child.field(0);
    static final RelType cntType = new RelType.VarType("CNT_type", true);

    @Override
    public RelRN before() {
        // COUNT(*) WITHIN DISTINCT (x)  is identical to  COUNT(DISTINCT x)
        return new RelRN.Aggregate(child, Seq.<RexRN>empty(),
                Seq.of(new RelRN.AggCall("count", true, cntType, Seq.of(x))));
    }

    @Override
    public RelRN after() {
        // Deduplicate rows by x (one row per distinct x), then COUNT the column
        // non-distinct over the deduplicated input.
        RelRN dedup = new RelRN.Aggregate(child, Seq.of(x), Seq.<RelRN.AggCall>empty());
        return new RelRN.Aggregate(dedup, Seq.<RexRN>empty(),
                Seq.of(new RelRN.AggCall("count", false, cntType, Seq.of(dedup.field(0)))));
    }
}
