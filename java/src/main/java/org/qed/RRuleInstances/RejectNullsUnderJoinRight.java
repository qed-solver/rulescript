package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — only the InnerJoin variant is modeled (InnerJoinApply/LeftJoin/LeftJoinApply/SemiJoin/AntiJoin are not), and null-rejection is restricted to a single explicit IS_NOT_NULL conjunct on the right input's column inside the ON condition
public record RejectNullsUnderJoinRight() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    // g: the join's ON condition (uninterpreted), unchanged by the rewrite.
    static final RexRN on = left.joinPred("on", right);

    // IS_NOT_NULL on the right input's column, as seen from above the join
    // (field 1 of the join row: left scan has 1 column).
    static final RexRN rejectOn =
            new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL, Seq.of(left.joinField(1, right)));

    // The full ON condition explicitly contains the null-rejecting conjunct.
    static final RexRN fullOn = RexRN.and(on, rejectOn);

    // IS_NOT_NULL on the same column, as seen within the right input alone.
    static final RexRN rejectBelow =
            new RexRN.Pred(SqlStdOperatorTable.IS_NOT_NULL, Seq.of(right.field(0)));

    @Override
    public RelRN before() {
        // InnerJoin(L, R, g AND IS_NOT_NULL(r))
        return left.join(JoinRelType.INNER, fullOn, right);
    }

    @Override
    public RelRN after() {
        // InnerJoin(L, Select(R, IS_NOT_NULL(r)), g AND IS_NOT_NULL(r))
        return left.join(JoinRelType.INNER, fullOn, right.filter(rejectBelow));
    }
}
