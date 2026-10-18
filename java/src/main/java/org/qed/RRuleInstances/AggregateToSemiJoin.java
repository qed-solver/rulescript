package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — INNER join only (not the rule's LEFT case), with both aggregates pure GROUP BY (zero aggregate calls) on single equi keys.
public record AggregateToSemiJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", RexRN.varType("Lk_Type", true), false);
    static final RelRN right = RelRN.scan("R", RexRN.varType("R_Type", true), false);
    static final RelRN rightAgg = new RelRN.Aggregate(right, Seq.of(right.field(0)), Seq.empty());

    @Override
    public RelRN before() {
        RelRN innerJoin = left.join(JoinRelType.INNER,
                new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                        Seq.of(left.joinField(0, rightAgg), left.joinField(1, rightAgg))),
                rightAgg);
        return new RelRN.Aggregate(innerJoin, Seq.of(innerJoin.field(0)), Seq.empty());
    }

    @Override
    public RelRN after() {
        RelRN semiJoin = left.join(JoinRelType.SEMI,
                new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                        Seq.of(left.joinField(0, right), left.joinField(1, right))),
                right);
        return new RelRN.Aggregate(semiJoin, Seq.of(semiJoin.field(0)), Seq.empty());
    }
}