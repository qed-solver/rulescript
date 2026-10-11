package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// SCOPE: PARTIAL — only the Le variant is encoded, proving a <= b <==> b >= a for two independent same-type column references (inner cross join of two single-column scans of one shared type) under three-valued semantics; Lt/Ge/Gt would need their own separate rule files.
public record CommuteVarInequality() implements RRule {
    static final RelRN left = RelRN.scan("L", "V");
    static final RelRN right = RelRN.scan("R", "V");

    // Identical join on both sides, so only the scalar filter is compared.
    static final RelRN join = left.join(JoinRelType.INNER, RexRN.trueLiteral(), right);
    static final RexRN x = join.field(0);
    static final RexRN y = join.field(1);

    @Override
    public RelRN before() {
        // Filter(x <= y, Join(L, R, true))
        return join.filter(new Pred(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, Seq.of(x, y)));
    }

    @Override
    public RelRN after() {
        // Filter(y >= x, Join(L, R, true))
        return join.filter(new Pred(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, Seq.of(y, x)));
    }
}
