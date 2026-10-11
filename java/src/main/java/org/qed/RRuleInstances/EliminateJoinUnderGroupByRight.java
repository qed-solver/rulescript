package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

// SCOPE: PARTIAL — the join is an INNER self-join on equality over a single non-nullable key column, and the grouping operator is a group-by with exactly one group key referencing the right (kept) input's column and no aggregate calls, so the left input is simply dropped.
public record EliminateJoinUnderGroupByRight() implements RRule {
    static final RelType.VarType T = RexRN.varType("T", false); // non-nullable
    static final RelRN s = RelRN.scan("S", T, false);            // non-unique

    // INNER join condition: S.col = S.col (self-join on equality)
    static final RexRN cond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(s.joinField(0, s), s.joinField(1, s)));

    static final RelRN join = s.join(JoinRelType.INNER, cond, s);

    // Before: group-by (a DISTINCT of the group key) over the INNER self-join.
    // The single group key references only the right (kept) input's column
    // (join.field(1)), matching the rule's requirement that only columns from
    // the right input are used by the grouping operator.
    static final RelRN beforeAgg = new RelRN.Aggregate(
            join,
            Seq.of(join.field(1)),
            Seq.empty());

    // After: the join and its left input are eliminated; the same group-by is
    // taken over the right input (S) alone. In a self-join on equality every
    // right row matches itself, so no right row is dropped, and the group-by
    // (with no aggregate calls) already collapses the duplication the join
    // introduced.
    static final RelRN afterAgg = new RelRN.Aggregate(
            s,
            Seq.of(s.field(0)),
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
