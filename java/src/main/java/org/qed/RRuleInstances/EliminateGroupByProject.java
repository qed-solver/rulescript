package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — assumes the GroupBy is a DistinctOn (no aggregate output columns) over a fixed 2-column base whose project child only drops the second column (synthesizing no new expressions, so OutputCols(project) ⊆ OutputCols(base)) and the group key is the retained first column.
public record EliminateGroupByProject() implements RRule {
    static final RelRN base = RelRN.scanMany("S",
            Seq.of(RexRN.varType("S_Type_0", true), RexRN.varType("S_Type_1", true)));

    // A project that only removes columns: it keeps the base's first column, drops the
    // second, and synthesizes no new expressions.
    static final RelRN project = base.project(Seq.of(base.field(0)));

    // before: DistinctOn (group by the retained first column, no aggs) fed by the
    // column-dropping project.  after: the identical DistinctOn applied directly to the
    // base, since the grouping ignores the dropped column.
    @Override
    public RelRN before() {
        return new RelRN.Aggregate(project, Seq.of(project.field(0)), Seq.empty());
    }

    @Override
    public RelRN after() {
        return new RelRN.Aggregate(base, Seq.of(base.field(0)), Seq.empty());
    }
}
