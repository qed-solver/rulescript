package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — the input has exactly two columns, which are the grouping columns (the DSL's scan has fixed arity); a no-agg GroupBy's output depends only on its grouping columns, so the only loss is the number of grouping columns.
public record ConvertGroupByToDistinct() implements RRule {
    // Input relation whose columns are exactly the grouping columns. Since a
    // GroupBy with no aggregations emits one row per distinct grouping tuple,
    // non-grouping input columns cannot affect the output, so modeling the
    // input as the grouping columns alone loses no semantic generality.
    static final RelRN input = RelRN.scanMany("Input", Seq.of(
            RexRN.varType("K1_Type", false),
            RexRN.varType("K2_Type", false)));

    @Override
    public RelRN before() {
        // GroupBy with zero aggregate calls: one row per distinct grouping tuple.
        return new RelRN.Aggregate(input, input.fields(0, 1), Seq.empty());
    }

    @Override
    public RelRN after() {
        // DistinctOn with no aggregations: the input deduplicated on all of
        // its columns, expressed as a set-variant self-intersection
        // (each distinct tuple appears exactly once).
        return input.intersect(false, input);
    }
}
