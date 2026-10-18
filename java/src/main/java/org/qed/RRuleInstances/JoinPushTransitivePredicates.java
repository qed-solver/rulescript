package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RelRN;
import org.qed.RRule;
import org.qed.RuleBuilder;
import org.qed.RexRN;

// ORIGIN: Calcite
// SCOPE: PARTIAL — INNER single-key equi-join, one uninterpreted right-input key predicate pushed to the equal left-input key only
public record JoinPushTransitivePredicates() implements RRule {
    static final SqlOperator eq = SqlStdOperatorTable.EQUALS;

    static final RelRN leftInput = RelRN.scan("LeftInput", RexRN.varType("Key_Type", false), false);
    static final RelRN rightInput = RelRN.scan("RightInput", RexRN.varType("Key_Type", false), false);

    static final SqlOperator p = RuleBuilder.create().genericPredicateOp("p", true);
    static final RexRN pRight = new RexRN.Pred(p, Seq.of(rightInput.field(0)));
    static final RexRN pLeft = new RexRN.Pred(p, Seq.of(leftInput.field(0)));

    static final RexRN joinCond = new RexRN.Pred(eq,
            Seq.of(leftInput.joinField(0, rightInput),
                   leftInput.joinField(1, rightInput)));

    static final RelRN filteredRight = rightInput.filter(pRight);

    @Override
    public RelRN before() {
        return leftInput.join(JoinRelType.INNER, joinCond, filteredRight);
    }

    @Override
    public RelRN after() {
        return leftInput.filter(pLeft)
                .join(JoinRelType.INNER, joinCond, filteredRight);
    }
}
