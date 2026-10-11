package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — assumes the DistinctOn's input is a base table whose single grouping column is statically known to be a strict key (declared unique) and there are no aggregate output columns, so the distinct is a plain group-by on that column and the replacement Project just projects it.
public record EliminateDistinct() implements RRule {
    static final RelRN source = RelRN.scan("S", RexRN.varType("S_Type", false), true);

    @Override
    public RelRN before() {
        return new RelRN.Aggregate(source, Seq.of(source.field(0)), Seq.empty());
    }

    @Override
    public RelRN after() {
        return source.project(source.field(0));
    }
}
