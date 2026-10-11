package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Calcite
public record JoinToSemiJoin() implements RRule {
    static final RelType.VarType T = RexRN.varType("T", true);
    static final RelRN left = RelRN.scan("L", T, false);
    static final RelRN rawRight = RelRN.scan("R", T, false);
    static final RelRN agg = new RelRN.Aggregate(rawRight, Seq.of(rawRight.field(0)), Seq.empty());

    static final RexRN joinCond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(left.joinField(0, agg), left.joinField(1, agg)));

    static final RexRN semiCond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(left.joinField(0, rawRight), left.joinField(1, rawRight)));

    @Override
    public RelRN before() {
        RelRN innerJoin = left.join(JoinRelType.INNER, joinCond, agg);
        return innerJoin.project(innerJoin.field(0));
    }

    @Override
    public RelRN after() {
        RelRN semiJoin = left.join(JoinRelType.SEMI, semiCond, rawRight);
        return semiJoin.project(semiJoin.field(0));
    }
}
