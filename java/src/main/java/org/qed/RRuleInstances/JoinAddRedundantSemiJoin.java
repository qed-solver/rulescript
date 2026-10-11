package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: FULL
public record JoinAddRedundantSemiJoin() implements RRule {
    static final RelRN X = RelRN.scan("X", "X_Type");
    static final RelRN Y = RelRN.scan("Y", "Y_Type");

    static final RexRN cond = X.joinPred("cond", Y);

    @Override
    public RelRN before() {
        return X.join(JoinRelType.INNER, cond, Y);
    }

    @Override
    public RelRN after() {
        return X.join(JoinRelType.SEMI, cond, Y)
                .join(JoinRelType.INNER, cond, Y);
    }
}
