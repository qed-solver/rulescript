package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// ORIGIN: Calcite
public record FullToLeftAndRightJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", "V");
    static final RelRN right = RelRN.scan("R", "V");

    static final RelRN joinRow = left.join(JoinRelType.FULL, RexRN.trueLiteral(), right);
    static final RexRN joinCond =
            new Pred(SqlStdOperatorTable.EQUALS, Seq.of(joinRow.field(0), joinRow.field(1)));

    static final RexRN notMatched =
            new Pred(SqlStdOperatorTable.IS_NOT_TRUE, Seq.of(joinCond));

    @Override
    public RelRN before() {
        return left.join(JoinRelType.FULL, joinCond, right);
    }

    @Override
    public RelRN after() {
        RelRN leftJoin = left.join(JoinRelType.LEFT, joinCond, right);
        RelRN rightJoin = left.join(JoinRelType.RIGHT, joinCond, right).filter(notMatched);
        return leftJoin.union(true, rightJoin);
    }
}