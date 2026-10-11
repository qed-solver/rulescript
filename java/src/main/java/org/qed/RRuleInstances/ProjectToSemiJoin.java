package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

public record ProjectToSemiJoin() implements RRule {
    static final RelType.VarType T = RexRN.varType("T", true);
    static final RelRN left = RelRN.scanMany("L", Seq.of(T, T));
    static final RelRN rawRight = RelRN.scanMany("R", Seq.of(T, T));
    static final RelRN agg = new RelRN.Aggregate(rawRight, Seq.of(rawRight.field(1)), Seq.empty());

    static final RexRN joinCond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(left.joinField(0, agg), left.joinField(2, agg)));

    static final RexRN semiCond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
            Seq.of(left.joinField(0, rawRight), left.joinField(3, rawRight)));

    @Override
    public RelRN before() {
        RelRN innerJoin = left.join(JoinRelType.INNER, joinCond, agg);
        return innerJoin.project(Seq.of(innerJoin.field(1), innerJoin.field(0)));
    }

    @Override
    public RelRN after() {
        RelRN semiJoin = left.join(JoinRelType.SEMI, semiCond, rawRight);
        return semiJoin.project(Seq.of(semiJoin.field(1), semiJoin.field(0)));
    }
}
