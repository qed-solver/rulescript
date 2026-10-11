package org.qed.RRuleInstances;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
public record JoinToCorrelate() implements RRule {
    static final RelRN left = RelRN.scan("L", RexRN.varType("T", true), false);
    static final RelRN right = RelRN.scan("R", RexRN.varType("T", true), false);
    static final SqlOperator cond = RuleBuilder.create().genericPredicateOp("cond", true);

    @Override
    public RelRN before() {
        return left.join(JoinRelType.INNER, new RexRN.Pred(cond, left.joinFields(right)), right);
    }

    @Override
    public RelRN after() {
        return left.correlate(JoinRelType.INNER, cond, right);
    }
}
