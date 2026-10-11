package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RelType;
import org.qed.RuleBuilder;

// SCOPE: PARTIAL — assumes an uncorrelated anti-join with a 1-column left, a 2-column right of which one column is pruned, and an uninterpreted on-clause over the kept (left, right-key) pair
public record PruneSemiAntiJoinRightCols() implements RRule {
    static final RelType.VarType t0 = new RelType.VarType("T0", true);
    static final RelType.VarType t1 = new RelType.VarType("T1", true);
    static final RelRN left = RelRN.scan("L", "L_Type");
    static final RelRN right = RelRN.scanMany("R", Seq.of(t0, t1));
    static final SqlOperator c = RuleBuilder.create().genericPredicateOp("C", true);
    @Override
    public RelRN before() {
        return left.join(JoinRelType.ANTI, new RexRN.Pred(c, Seq.of(right.joinField(1, left), right.joinField(0, left))), right);
    }
    @Override
    public RelRN after() {
        RelRN rp = right.project(right.field(0));
        return left.join(JoinRelType.ANTI, new RexRN.Pred(c, Seq.of(rp.joinField(1, left), rp.joinField(0, left))), rp);
    }
}