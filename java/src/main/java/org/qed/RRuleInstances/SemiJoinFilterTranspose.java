package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: FULL
public record SemiJoinFilterTranspose() implements RRule {
    static final RelRN X = RelRN.scanMany("X", Seq.of(
            RexRN.varType("X0_Type", true), RexRN.varType("X1_Type", true)));
    static final RelRN Y = RelRN.scanMany("Y", Seq.of(
            RexRN.varType("Y0_Type", true), RexRN.varType("Y1_Type", true)));

    static final RexRN cond = X.joinPred("join_cond", Y);
    static final RexRN filterPred = X.pred("filter");

    @Override
    public RelRN before() {
        return X.filter(filterPred).join(JoinRelType.SEMI, cond, Y);
    }

    @Override
    public RelRN after() {
        RelRN semi = X.join(JoinRelType.SEMI, cond, Y);
        return semi.filter(filterPred);
    }
}
