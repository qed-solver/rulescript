package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — the input has exactly two columns (the DSL's fixed scan arity), which are all the columns DISTINCT deduplicates on, and the Distinct node is modeled as a set-variant self-intersection, the only deduplication construct QED models.
public record ReplaceDistinctWithAggregate() implements RRule {
    // The child of the Distinct node: its output attributes are exactly the
    // columns the rule groups by (Aggregate(child.output, child.output, child)),
    // so modeling the child's schema as those columns loses nothing.
    static final RelRN input = RelRN.scanMany("Input", Seq.of(
            RexRN.varType("Col0_Type", false),
            RexRN.varType("Col1_Type", false)));

    @Override
    public RelRN before() {
        // Distinct(input): every distinct input row appears exactly once,
        // modeled as a set-variant self-intersection (all=false, the only
        // INTERSECT variant QED models).
        return input.intersect(false, input);
    }

    @Override
    public RelRN after() {
        // Aggregate(input, group by all columns, no aggregate calls): one
        // output row per distinct input tuple, with exactly the grouping
        // (i.e. all) input columns — Spark's Aggregate(child.output,
        // child.output, child).
        return new RelRN.Aggregate(input, input.fields(0, 1), Seq.empty());
    }
}
