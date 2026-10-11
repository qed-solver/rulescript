package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the 2-input, single-column instance in which both inputs share one row type, so the rule's type-unification casts are identity (the general n-way rule is obtained by repeated application of this binary step).
public record MinusToAntiJoin() implements RRule {
    static final RelRN a = RelRN.scan("A", "T");
    static final RelRN b = RelRN.scan("B", "T");

    static final RexRN cond = new RexRN.Pred(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
            Seq.of(a.joinField(0, b), a.joinField(1, b)));

    @Override
    public RelRN before() {
        return a.minus(false, b);
    }

    @Override
    public RelRN after() {
        RelRN anti = a.join(JoinRelType.ANTI, cond, b);
        return new RelRN.Aggregate(anti, Seq.of(anti.field(0)), Seq.empty());
    }
}