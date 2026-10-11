package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;


public record JoinUnionTranspose() implements RRule {
    static final RelRN X = RelRN.scan("X", "T");
    static final RelRN Y = RelRN.scan("Y", "T");
    static final RelRN O = RelRN.scan("O", "S");

    static final SqlOperator C = RuleBuilder.create().genericPredicateOp("C", true);

    static final RelRN U = X.union(true, Y);

    static final RexRN condU = U.joinPred(C, O);
    static final RexRN condX = X.joinPred(C, O);
    static final RexRN condY = Y.joinPred(C, O);

    @Override
    public RelRN before() {
        return U.join(JoinRelType.INNER, condU, O);
    }

    @Override
    public RelRN after() {
        return X.join(JoinRelType.INNER, condX, O)
                .union(true,
                       Y.join(JoinRelType.INNER, condY, O));
    }
}
