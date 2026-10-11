package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// SCOPE: PARTIAL — LEFT join only
public record RemoveJoinNotNullCondition() implements RRule {
    static final RelRN left  = RelRN.scan("L", RexRN.varType("L_Type", false), false);
    static final RelRN right = RelRN.scan("R", "R_Type");
    static final RexRN notNull = left.joinField(0, right).pred(SqlStdOperatorTable.IS_NOT_NULL);
    static final RexRN rest    = left.joinPred("rest", right);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.LEFT, RexRN.and(notNull, rest), right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.LEFT, rest, right);
    }
}
