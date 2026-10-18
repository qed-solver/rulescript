package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — only the dedup branch (no-aggregate-call GROUP BY over all columns ≡ DISTINCT); the empty-Values grand-total branch requires aggregate-function algebra and empty-input semantics that QED does not model.
public record AggregateValues() implements RRule {
    static final RelRN source = RelRN.scan("S", "S_Type");

    @Override
    public RelRN before() {
        return new RelRN.Aggregate(source, Seq.of(source.field(0)), Seq.empty());
    }

    @Override
    public RelRN after() {
        return source.union(false, source);
    }
}
