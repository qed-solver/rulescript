package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RelType;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — the 2-input, 2-column instance in which both inputs share one row type; the general n-column rule is the same construction with a longer conjunction of null-safe equalities.
public record ReplaceIntersectWithSemiJoin() implements RRule {
    static final RelType.VarType c1 = RexRN.varType("C1", true);
    static final RelType.VarType c2 = RexRN.varType("C2", true);

    static final RelRN a = RelRN.scanMany("A", Seq.of(c1, c2));
    static final RelRN b = RelRN.scanMany("B", Seq.of(c1, c2));

    // Per-column join conditions: A.ci IS NOT DISTINCT FROM B.ci (Spark's EqualNullSafe = <=>)
    static final RexRN cond1 = new RexRN.Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
            Seq.of(a.joinField(0, b), a.joinField(2, b)));
    static final RexRN cond2 = new RexRN.Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
            Seq.of(a.joinField(1, b), a.joinField(3, b)));

    @Override
    public RelRN before() {
        // INTERSECT DISTINCT (all = false)
        return a.intersect(false, b);
    }

    @Override
    public RelRN after() {
        // A SEMI-JOIN B ON (A.c1 <=> B.c1 AND A.c2 <=> B.c2), then DISTINCT
        // (Spark's Distinct(...) = aggregate group-by-all, no agg calls)
        RelRN semi = a.join(JoinRelType.SEMI, RexRN.and(cond1, cond2), b);
        return new RelRN.Aggregate(semi, Seq.of(semi.field(0), semi.field(1)), Seq.empty());
    }
}
