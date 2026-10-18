package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RelType;

// ORIGIN: Spark
// SCOPE: PARTIAL — the dedup keys must equal all of the child's output columns, so the rewrite reduces to DISTINCT ≡ GROUP BY all columns with no aggregate calls (no First() involved)
public record ReplaceDeduplicateWithAggregate() implements RRule {
    static final RelRN source = RelRN.scanMany("Source", Seq.of(
            new RelType.VarType("C1_type", true),
            new RelType.VarType("C2_type", true)));

    @Override
    public RelRN before() {
        // Deduplicate(keys = all columns) keeps exactly one row per distinct
        // full row: DISTINCT(source), modeled as set self-intersection.
        return source.intersect(false, source);
    }

    @Override
    public RelRN after() {
        // Group by all output columns, no aggregate calls: one row per distinct
        // full row, output exactly the columns themselves.
        return new RelRN.Aggregate(source, source.fields(), Seq.<RelRN.AggCall>empty());
    }
}
