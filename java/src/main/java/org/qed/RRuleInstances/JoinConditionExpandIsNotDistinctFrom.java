package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;
import org.qed.RexRN.Pred;

// ORIGIN: Calcite
// SCOPE: PARTIAL — IS NOT DISTINCT FROM is the entire condition of an inner join between two single-column scans of a single shared type, with the 3-valued-logic expansion on the right-hand side rather than Calcite's COALESCE-based form
public record JoinConditionExpandIsNotDistinctFrom() implements RRule {
    static final RelRN left = RelRN.scan("L", "V");
    static final RelRN right = RelRN.scan("R", "V");

    static final RexRN x = left.joinField(0, right);
    static final RexRN y = left.joinField(1, right);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER,
                new Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, Seq.of(x, y)),
                right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER,
                new RexRN.Or(Seq.of(
                        new RexRN.And(Seq.of(
                                new Pred(SqlStdOperatorTable.IS_NULL, Seq.of(x)),
                                new Pred(SqlStdOperatorTable.IS_NULL, Seq.of(y)))),
                        new Pred(SqlStdOperatorTable.IS_TRUE,
                                Seq.of(new Pred(SqlStdOperatorTable.EQUALS, Seq.of(x, y)))))),
                right);
    }
}
