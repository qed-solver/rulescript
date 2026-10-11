package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// SCOPE: PARTIAL — the join is a LEFT join of two one-column scans, and the grouping operator is a group-by with exactly one group key referencing the left (preserved) input's column and no aggregate calls, so the rule's ProjectRemappedCols/PruneOrdering wrapper is the identity and the right input is simply dropped.
public record EliminateJoinUnderGroupByLeft() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    // Uninterpreted join condition over (L's column, R's column).
    static final RexRN joinCond = left.joinPred("join_cond", right);

    static final RelRN join = left.join(JoinRelType.LEFT, joinCond, right);

    // Before: group-by (a DISTINCT of the group key) over the LEFT join.
    // The single group key references only the preserved left input's
    // column (join.field(0)), matching the rule's requirement that only
    // columns from the left input are used by the grouping operator.
    static final RelRN beforeAgg = new RelRN.Aggregate(
            join,
            Seq.of(join.field(0)),
            Seq.empty());

    // After: the join and its right input are eliminated; the same
    // group-by is taken over the left input alone. A LEFT join preserves
    // every left row (each appears at least once), so dropping it does not
    // change the set of group keys — the group-by already collapses any
    // duplication of left rows the join may have introduced.
    static final RelRN afterAgg = new RelRN.Aggregate(
            left,
            Seq.of(left.field(0)),
            Seq.empty());

    @Override
    public RelRN before() {
        return beforeAgg;
    }

    @Override
    public RelRN after() {
        return afterAgg;
    }
}
