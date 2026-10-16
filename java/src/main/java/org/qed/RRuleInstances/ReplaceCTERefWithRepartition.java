package org.qed.RRuleInstances;

import org.qed.RRule;
import org.qed.RelRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — only the per-reference bag-level core is covered: a CTE reference (a transparent logical alias of the CTE definition) is replaced by a Repartition over the definition, proved as the repartition shuffle being bag-identical to its input; the multi-reference shuffle reuse, the canSkipExtraRepartition/underSubquery guards, and the output-set-mismatch self-join re-aliasing are physical/attribute-level effects QED does not model.
public record ReplaceCTERefWithRepartition() implements RRule {
    // The CTE definition as one uninterpreted relation. A CTERelationRef is a
    // transparent logical alias of the definition, so the reference is just
    // another use of the same symbol.
    static final RelRN cte = RelRN.scan("Cte", "Cte_Type");

    // A Repartition / RepartitionByExpression redistributes the input's rows
    // across partitions without changing the rows or their multiplicities, so
    // its bag semantics is exactly an identity projection of its input — the
    // shuffle is invisible at the bag level.
    static RelRN repartition(RelRN input) {
        return input.project(input.fields());
    }

    @Override
    public RelRN before() {
        // A CTE reference still in the plan: it just reads the CTE definition.
        return cte;
    }

    @Override
    public RelRN after() {
        // The reference is replaced by a repartition over the CTE definition so
        // the shuffle can be planned once and reused across reference points.
        return repartition(cte);
    }
}
