package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — the null-rejecting filter is restricted to an explicit IS_NOT_NULL on the left input's column (the FullJoin→LeftJoin variant of the rule)
public record RejectNullsRightJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    // g: the join's ON condition, unchanged by the rewrite.
    static final RexRN on = left.joinPred("on", right);

    // The null-rejecting filter, seen from above the join: IS_NOT_NULL on the
    // left side's column as it appears in the join row (field 0). This rejects
    // the full join's NULL-extended (no right match) rows.
    static final RexRN rejectAbove =
            new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL, Seq.of(left.joinField(0, right)));

    @Override
    public RelRN before() {
        // Select(FullJoin(L, R, g), IS_NOT_NULL(l)) — the filter sits above
        // the join and rejects the full join's NULL-extended left side.
        return left.join(JoinRelType.FULL, on, right).filter(rejectAbove);
    }

    @Override
    public RelRN after() {
        // Select(LeftJoin(L, R, g), IS_NOT_NULL(l)) — only the join kind is
        // flipped (FULL→LEFT); the same filter remains above the join,
        // exactly as in the source rule (no pushdown).
        return left.join(JoinRelType.LEFT, on, right).filter(rejectAbove);
    }
}