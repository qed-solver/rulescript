// SCOPE: PARTIAL — the INNER-join variant only, on a self-join of the same unique single non-nullable-column scan on equality of that column, with the project using only the preserved right column
package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

public record EliminateJoinUnderProjectRight() implements RRule {
    // Single NON-NULLABLE column and scan UNIQUE on that column: every row
    // matches exactly itself (non-null, so r.c = r.c holds, and at most one
    // row matches — the key — so no right row is duplicated and none is
    // filtered), i.e. JoinDoesNotDuplicateRightRows and
    // JoinPreservesRightRows hold structurally for the INNER self-join.
    static final RelType.VarType V = new RelType.VarType("V", false);
    static final RelRN t = RelRN.scan("T", V, true);

    // Helper join (true condition) to name the 2-column join-output layout:
    // field 0 = left instance's column, field 1 = right instance's column.
    static final RelRN joinRow = t.join(JoinRelType.INNER, RexRN.trueLiteral(), t);

    // Equi-join on the unique key column: T.col = T.col
    static final RexRN cond = new Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(joinRow.field(0), joinRow.field(1)));

    @Override
    public RelRN before() {
        // Project over the INNER self-join keeping only the preserved right
        // column (no reference to the left input's column).
        RelRN join = t.join(JoinRelType.INNER, cond, t);
        return join.project(join.field(1));
    }

    @Override
    public RelRN after() {
        // Join removed: project the same column straight from the scan
        // (the right input).
        return t.project(t.field(0));
    }
}
