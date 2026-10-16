package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — 2-column instance; both EXCEPT operands share one row type as Spark's analyzer guarantees
public record ReplaceExceptWithAntiJoin() implements RRule {
    static final RelRN a = RelRN.scanMany("A", Seq.of(
            new RelType.VarType("C0", true),
            new RelType.VarType("C1", true)));
    static final RelRN b = RelRN.scanMany("B", Seq.of(
            new RelType.VarType("C0", true),
            new RelType.VarType("C1", true)));
    static final RexRN pair0 = new RexRN.Pred(
            SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
            Seq.of(a.joinField(0, b), a.joinField(2, b)));
    static final RexRN pair1 = new RexRN.Pred(
            SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
            Seq.of(a.joinField(1, b), a.joinField(3, b)));
    static final RexRN cond = RexRN.and(pair0, pair1);

    @Override
    public RelRN before() {
        return a.minus(false, b);
    }

    @Override
    public RelRN after() {
        RelRN anti = a.join(JoinRelType.ANTI, cond, b);
        return new RelRN.Aggregate(anti, Seq.of(anti.field(0), anti.field(1)), Seq.empty());
    }
}