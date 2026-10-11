package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;

// ORIGIN: Calcite
// SCOPE: PARTIAL — the 2-input, single-column instance in which both inputs share one row type, so the rule's type-unification casts are identity (the general n-way rule is obtained by repeated application of this binary step)
public record IntersectToExists() implements RRule {
    static final RelRN a = RelRN.scan("A", "T");
    static final RelRN b = RelRN.scan("B", "T");

    @Override
    public RelRN before() {
        return a.intersect(false, b);
    }

    @Override
    public RelRN after() {
        RelRN exists = a.correlate(JoinRelType.SEMI,
                SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, b);
        return new RelRN.Aggregate(exists, Seq.of(exists.field(0)), Seq.empty());
    }
}
