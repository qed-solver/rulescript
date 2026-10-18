package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — single-column base (DSL's fixed scan arity), so the rule's multi-column sameResult check and attribute-by-name remapping are vacuous.
public record ReplaceExceptWithFilter() implements RRule {
    static final RelRN base = RelRN.scan("Base", "Base_Type");
    static final RexRN pL = base.pred("P_L");
    static final RexRN pR = base.pred("P_R");

    // Not(COALESCE(P_R, FALSE)) = Not(P_R) OR IS_NULL(P_R)  (3-valued logic)
    static final RexRN notCoalesce = new RexRN.Or(Seq.of(
        new RexRN.Not(pR),
        pR.pred(SqlStdOperatorTable.IS_NULL)
    ));

    @Override
    public RelRN before() {
        return base.filter(pL).minus(false, base.filter(pR));
    }

    @Override
    public RelRN after() {
        RelRN f = base.filter(pL).filter(notCoalesce);
        return f.intersect(false, f);
    }
}
