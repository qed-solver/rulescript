package org.qed.RRuleInstances;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RelRN;
import org.qed.RexRN;
import org.qed.RuleBuilder;

// ORIGIN: Calcite
// SCOPE: PARTIAL — a window modeled as a single partition-by group with one uninterpreted aggregate window function over one operand expression, reduced from `FALSE AND P(v)` to the constant FALSE; constant partition-key and order-key removal driven by pulled-up input predicates, frames, and multiple groups are not encoded
public record WindowReduceExpressions() implements RRule {
    static final RelRN base = RelRN.scanMany("B", Seq.of(
            RexRN.varType("K_Type", true),
            RexRN.varType("V_Type", true)));

    static final SqlOperator P = RuleBuilder.create().genericPredicateOp("P", true);

    static final RelRN operandBefore = base.project(Seq.of(
            base.field(0),
            RexRN.and(RexRN.falseLiteral(), new RexRN.Pred(P, Seq.of(base.field(1))))));
    static final RelRN operandAfter = base.project(Seq.of(base.field(0), RexRN.falseLiteral()));

    static RelRN window(RelRN operand) {
        RelRN perPart = new RelRN.Aggregate(operand, Seq.of(operand.field(0)),
                Seq.of(operand.field(1).aggCall("f")));
        RexRN cond = new RexRN.Pred(SqlStdOperatorTable.EQUALS,
                Seq.of(base.joinField(0, perPart), base.joinField(2, perPart)));
        return base.join(JoinRelType.INNER, cond, perPart);
    }

    @Override
    public RelRN before() {
        return window(operandBefore);
    }

    @Override
    public RelRN after() {
        return window(operandAfter);
    }
}
