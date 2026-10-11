package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// SCOPE: PARTIAL — 2-input, single-column, same row type, identity column mapping, with the right input a base scan declared unique (has a strict key).
public record SimplifyIntersectRight() implements RRule {
    // Right has a strict key (unique); left does not.
    static final RelRN left = RelRN.scan("L", RexRN.varType("T", false), false);
    static final RelRN right = RelRN.scan("R", RexRN.varType("T", false), true);

    // Semi-join condition: R.col IS NOT DISTINCT FROM L.col
    static final RexRN cond = new RexRN.Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
            Seq.of(right.joinField(0, left), right.joinField(1, left)));

    @Override
    public RelRN before() {
        // INTERSECT (set semantics, all = false)
        return left.intersect(false, right);
    }

    @Override
    public RelRN after() {
        // Because R has a strict key, INTERSECT ALL reduces to R SEMI-JOIN L; no distinct step needed.
        return right.join(JoinRelType.SEMI, cond, left);
    }
}
