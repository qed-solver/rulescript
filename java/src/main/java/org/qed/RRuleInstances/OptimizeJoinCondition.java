package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// ORIGIN: Spark
// SCOPE: PARTIAL — the pattern is the entire condition of an inner join between two single-column scans of a shared type, rather than a (nested) subterm of the condition of a join of any kind
public record OptimizeJoinCondition() implements RRule {
    static final RelRN left = RelRN.scan("L", "V");
    static final RelRN right = RelRN.scan("R", "V");

    // Fields of the combined join row: L's column and R's column.
    static final RexRN x = left.joinField(0, right);
    static final RexRN y = left.joinField(1, right);

    @Override
    public RelRN before() {
        // Join(L, R, (x = y) OR (x IS NULL AND y IS NULL))
        return left.join(JoinRelType.INNER,
                new RexRN.Or(Seq.of(
                        new Pred(SqlStdOperatorTable.EQUALS, Seq.of(x, y)),
                        new RexRN.And(Seq.of(
                                new Pred(SqlStdOperatorTable.IS_NULL, Seq.of(x)),
                                new Pred(SqlStdOperatorTable.IS_NULL, Seq.of(y)))))),
                right);
    }

    @Override
    public RelRN after() {
        // Join(L, R, x IS NOT DISTINCT FROM y)  (Spark's EqualNullSafe l, r)
        return left.join(JoinRelType.INNER,
                new Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, Seq.of(x, y)),
                right);
    }
}
