package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// SCOPE: PARTIAL — self-join in which both inputs are the same single non-nullable-column scan joined on equality of that column
public record SimplifyLeftJoin() implements RRule {
    // Single NON-NULLABLE column: ensures a left row's column value always
    // equals itself, so every left row matches at least its own copy in the
    // (identical) right input — structurally guaranteeing the "every left row
    // matches at least one right row" condition of SimplifyLeftJoin.
    static final RelType.VarType V = new RelType.VarType("V", false);
    static final RelRN tbl = RelRN.scan("T", V, false);

    // The two-column join-output row (left.col, right.col); both the LEFT and
    // INNER joins over T and T share this layout, so the same equality
    // condition applies to both.
    static final RelRN joinRow = tbl.join(JoinRelType.LEFT, RexRN.trueLiteral(), tbl);
    static final RexRN cond =
            new Pred(SqlStdOperatorTable.EQUALS, Seq.of(joinRow.field(0), joinRow.field(1)));

    @Override
    public RelRN before() {
        return tbl.join(JoinRelType.LEFT, cond, tbl);
    }

    @Override
    public RelRN after() {
        return tbl.join(JoinRelType.INNER, cond, tbl);
    }
}
