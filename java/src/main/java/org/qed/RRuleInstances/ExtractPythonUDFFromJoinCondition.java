package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: FULL
public record ExtractPythonUDFFromJoinCondition() implements RRule {
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scan("R", "R_Type");

    // The partitioned-out conjunctive part of the join condition that
    // contains an unevaluable Python UDF (may be a conjunction of several).
    static final RexRN udf = left.pred("udf");
    // The remaining conjunctive part of the join condition (the "rest"),
    // kept as the join's new condition.
    static final RexRN rest = left.pred("rest");

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, RexRN.and(udf, rest), right);
    }

    @Override
    public RelRN after() {
        // Inner-like join only: the UDF part is lifted to a filter on top of
        // a join whose condition is the rest (cartesian when rest == true).
        return left.join(JoinRelType.INNER, rest, right).filter(udf);
    }
}
