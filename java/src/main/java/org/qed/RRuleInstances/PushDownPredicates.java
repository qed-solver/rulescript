package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Spark
// SCOPE: PARTIAL — INNER join only: a single left-only parent-filter conjunct is pushed below the join, leaving the existing join condition unchanged (right-only/common conjuncts and all other dispatcher cases are omitted).
public record PushDownPredicates() implements RRule {
    static final RelRN L = RelRN.scan("L", "L_Type");
    static final RelRN R = RelRN.scan("R", "R_Type");
    static final RexRN joinCond = L.joinPred("join_cond", R);
    static final RexRN fAbove = L.joinField(0, R).pred("left_only");
    static final RexRN fLeft = L.field(0).pred("left_only");

    @Override
    public RelRN before() {
        return L.join(JoinRelType.INNER, joinCond, R).filter(fAbove);
    }

    @Override
    public RelRN after() {
        return L.filter(fLeft).join(JoinRelType.INNER, joinCond, R);
    }
}
