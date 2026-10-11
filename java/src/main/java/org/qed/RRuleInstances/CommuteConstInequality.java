package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// SCOPE: PARTIAL — encodes only the Lt flip (x < y becomes y > x); the Le, Gt, and Ge commutation pairs of the original rule are not covered by this single record.
public record CommuteConstInequality() implements RRule {
    static final RelRN left = RelRN.scan("L", "V");
    static final RelRN right = RelRN.scan("R", "V");
    static final RelRN join = left.join(JoinRelType.INNER, RexRN.trueLiteral(), right);
    static final RexRN x = join.field(0);
    static final RexRN y = join.field(1);

    @Override
    public RelRN before() {
        return join.filter(new Pred(SqlStdOperatorTable.LESS_THAN, Seq.of(x, y)));
    }

    @Override
    public RelRN after() {
        return join.filter(new Pred(SqlStdOperatorTable.GREATER_THAN, Seq.of(y, x)));
    }
}
