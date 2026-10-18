package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRule;
import org.qed.RRuleInstances.OptimizeSubqueries;
import org.qed.RuleBuilder;

public class OptimizeSubqueriesTest {
    public static void runTest() {
        var rule = new OptimizeSubqueries();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var sourceBuilder = RuleBuilder.create();
        var left = RuleChecks.table(sourceBuilder, 1, false);
        var right = RuleChecks.table(sourceBuilder, 3, false);
        var b = RuleChecks.ruleBuilder(sourceBuilder);
        b.scan(left);
        b.scan(right);
        var leftInput = b.peek(1);
        var rightInput = b.peek(0);
        var rexBuilder = b.getRexBuilder();
        var cond = rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.NOT,
                rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                        rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.IS_NULL,
                                rexBuilder.makeInputRef(leftInput.getRowType().getFieldList().get(0).getType(), 0)),
                        rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.IS_NULL,
                                rexBuilder.makeInputRef(rightInput.getRowType().getFieldList().get(0).getType(), 1)),
                        rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.IS_NULL,
                                rexBuilder.makeInputRef(rightInput.getRowType().getFieldList().get(2).getType(), 3))));
        b.join(JoinRelType.SEMI, cond);
        var before = b.build();

        var expectedBuilder = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedBuilder, 1, false);
        var expectedRight = RuleChecks.table(expectedBuilder, 3, false);
        var e = RuleChecks.ruleBuilder(expectedBuilder);
        e.scan(expectedLeft);
        e.scan(expectedRight);
        e.project(e.field(0), e.field(2));
        var expectedLeftInput = e.peek(1);
        var expectedRightInput = e.peek(0);
        var expectedRexBuilder = e.getRexBuilder();
        var expectedCond = expectedRexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.NOT,
                expectedRexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                        expectedRexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.IS_NULL,
                                expectedRexBuilder.makeInputRef(expectedLeftInput.getRowType().getFieldList().get(0).getType(), 0)),
                        expectedRexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.IS_NULL,
                                expectedRexBuilder.makeInputRef(expectedRightInput.getRowType().getFieldList().get(0).getType(), 1)),
                        expectedRexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.IS_NULL,
                                expectedRexBuilder.makeInputRef(expectedRightInput.getRowType().getFieldList().get(1).getType(), 2))));
        e.join(JoinRelType.SEMI, expectedCond);
        var after = e.build();
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running OptimizeSubqueries test...");
        runTest();
    }
}
