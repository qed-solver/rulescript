package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.RightAssociateJoinsLeft;
import org.qed.RuleBuilder;

public class RightAssociateJoinsLeftTest {
    public static void runTest() {
        var rule = new RightAssociateJoinsLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var qedBuilder = RuleBuilder.create();
        var aName = RuleChecks.table(qedBuilder, 1, false);
        var bName = RuleChecks.table(qedBuilder, 1, false);
        var cName = RuleChecks.table(qedBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        RelNode a = builder.scan(aName).build();
        RelNode b = builder.scan(bName).build();
        RelNode c = builder.scan(cName).build();
        var rex = builder.getRexBuilder();
        RexNode ab = rex.makeCall(SqlStdOperatorTable.EQUALS,
                rex.makeInputRef(a.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(b.getRowType().getFieldList().get(0).getType(), 1));
        RexNode ac = rex.makeCall(SqlStdOperatorTable.GREATER_THAN,
                rex.makeInputRef(a.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(c.getRowType().getFieldList().get(0).getType(), 2));
        RexNode bcInner = rex.makeLiteral(true);
        RexNode beforeCondition = rex.makeCall(SqlStdOperatorTable.AND, ab, ac);
        RelNode bc = LogicalJoin.create(b, c, java.util.List.of(), bcInner, java.util.Set.of(), JoinRelType.INNER);
        RelNode before = LogicalJoin.create(a, bc, java.util.List.of(), beforeCondition, java.util.Set.of(), JoinRelType.INNER);

        RexNode abAfter = rex.makeCall(SqlStdOperatorTable.EQUALS,
                rex.makeInputRef(a.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(b.getRowType().getFieldList().get(0).getType(), 1));
        RelNode abJoin = LogicalJoin.create(a, b, java.util.List.of(), abAfter, java.util.Set.of(), JoinRelType.INNER);
        RexNode acAfter = rex.makeCall(SqlStdOperatorTable.GREATER_THAN,
                rex.makeInputRef(a.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(c.getRowType().getFieldList().get(0).getType(), 2));
        RelNode expected = LogicalJoin.create(abJoin, c, java.util.List.of(), acAfter,
                java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, expected);

        RelNode wrongInner = LogicalJoin.create(b, c, java.util.List.of(),
                rex.makeCall(SqlStdOperatorTable.EQUALS,
                        rex.makeInputRef(b.getRowType().getFieldList().get(0).getType(), 0),
                        rex.makeInputRef(c.getRowType().getFieldList().get(0).getType(), 1)),
                java.util.Set.of(), JoinRelType.INNER);
        RelNode nearMiss = LogicalJoin.create(a, wrongInner, java.util.List.of(), beforeCondition,
                java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RightAssociateJoinsLeft test...");
        runTest();
    }
}
