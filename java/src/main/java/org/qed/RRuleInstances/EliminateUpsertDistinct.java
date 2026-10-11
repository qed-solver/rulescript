package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — assumes the UpsertDistinctOn's input is a base table whose single grouping column is a lax key (declared unique but nullable, so at most one NULL row) and there are no aggregate output columns, so the UpsertDistinctOn is a plain group-by on that column and the replacement Project just projects it.
public record EliminateUpsertDistinct() implements RRule {
    static final RelRN source = RelRN.scan("S", RexRN.varType("S_Type", true), true);

    @Override
    public RelRN before() {
        return new RelRN.Aggregate(source, Seq.of(source.field(0)), Seq.empty());
    }

    @Override
    public RelRN after() {
        return source.project(source.field(0));
    }
}
