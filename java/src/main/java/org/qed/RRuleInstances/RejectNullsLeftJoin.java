package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — the null-rejecting filter is restricted to an explicit IS_NOT_NULL on the right input's column, and only the LeftJoin→InnerJoin variant is modeled (FullJoin→RightJoin and the Apply variants are not)
public record RejectNullsLeftJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    // g: the join's ON condition, unchanged by the rewrite.
    static final RexRN on = left.joinPred("on", right);

    // The null-rejecting filter, seen from above the join: IS_NOT_NULL on the
    // right side's column as it appears in the join row (field 1). This
    // rejects the left join's NULL-extended (no-match) rows.
    static final RexRN rejectAbove =
            new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL, Seq.of(left.joinField(1, right)));

    @Override
    public RelRN before() {
        // Select(LeftJoin(L, R, g), IS_NOT_NULL(r)) — the filter sits above
        // the join and rejects the left join's NULL-extended right side.
        return left.join(JoinRelType.LEFT, on, right).filter(rejectAbove);
    }

    @Override
    public RelRN after() {
        // Select(InnerJoin(L, R, g), IS_NOT_NULL(r)) — only the join kind is
        // flipped (LEFT→INNER); the same filter remains above the join,
        // exactly as in the source rule (no pushdown).
        return left.join(JoinRelType.INNER, on, right).filter(rejectAbove);
    }
}
