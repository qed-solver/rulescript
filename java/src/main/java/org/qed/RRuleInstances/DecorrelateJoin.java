package org.qed.RRuleInstances;

// SCOPE: PARTIAL — INNER join only; condition is a single uninterpreted binary predicate over one left column and one right column
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public record DecorrelateJoin() implements RRule {
    static final RelRN left = RelRN.scan("L", RexRN.varType("T", true), false);
    static final RelRN right = RelRN.scan("R", RexRN.varType("T", true), false);
    static final SqlOperator cond = RuleBuilder.create().genericPredicateOp("cond", true);

    @Override
    public RelRN before() {
        return left.correlate(JoinRelType.INNER, cond, right);
    }

    @Override
    public RelRN after() {
        return left.join(JoinRelType.INNER, new RexRN.Pred(cond, left.joinFields(right)), right);
    }
}