package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — the non-null redundant grouping column is assumed to be literally the same field as the surviving grouping column (a 2-column input whose two grouping columns are equal), and the UpsertDistinctOn has no aggregation output columns.
public record ReduceNotNullGroupingCols() implements RRule {
    // Non-null key type: matches the source rule's NotNullCols precondition on the
    // redundant column (in this special case the redundant column is K_Type itself).
    static final RelRN base = RelRN.scan("S", RexRN.varType("K_Type", false), false);

    // Two-column input whose two grouping columns are literally the same field:
    // the strongest possible form of "r is redundant given g" — r IS g.
    static final RelRN input = base.project(Seq.of(base.field(0), base.field(0)));

    @Override
    public RelRN before() {
        // UpsertDistinctOn grouping on both columns (g and the redundant r = g),
        // with no aggregation columns.
        return new RelRN.Aggregate(input,
                Seq.of(input.field(0), input.field(1)), Seq.empty());
    }

    @Override
    public RelRN after() {
        // Remove the redundant column from the grouping; its value — identical to
        // the surviving key's — is kept as the appended ConstAgg output column,
        // which is exactly a re-projection of the surviving key.
        RelRN agg = new RelRN.Aggregate(input, Seq.of(input.field(0)), Seq.empty());
        return agg.project(Seq.of(agg.field(0), agg.field(0)));
    }
}