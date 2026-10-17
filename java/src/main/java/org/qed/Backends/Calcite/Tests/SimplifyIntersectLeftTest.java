package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyIntersectLeft;
import org.qed.RuleBuilder;

import java.util.List;

public class SimplifyIntersectLeftTest {
    public static void runTest() {
        var rule = new SimplifyIntersectLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        rewritesDirectLogicalIntersect(rule);
        rejectsIntersectWithNonUniqueLeft(rule);
    }

    private static void rewritesDirectLogicalIntersect(SimplifyIntersectLeft rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, true, false);
        var rightName = RuleChecks.table(builder, 1, false, false);
        RelNode left = builder.scan(leftName).build();
        RelNode right = builder.scan(rightName).build();
        RelNode before = LogicalIntersect.create(List.of(left, right), false);

        var expectedBuilder = RuleBuilder.create();
        var expectedLeftName = RuleChecks.table(expectedBuilder, 1, true, false);
        var expectedRightName = RuleChecks.table(expectedBuilder, 1, false, false);
        expectedBuilder.scan(expectedLeftName).scan(expectedRightName);
        var rexBuilder = expectedBuilder.getRexBuilder();
        var leftRef = rexBuilder.makeInputRef(expectedBuilder.peek(1).getRowType().getFieldList().get(0).getType(), 0);
        var rightRef = rexBuilder.makeInputRef(expectedBuilder.peek(0).getRowType().getFieldList().get(0).getType(), 1);
        RelNode expected = expectedBuilder.join(JoinRelType.SEMI,
                expectedBuilder.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, leftRef, rightRef)).build();
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsIntersectWithNonUniqueLeft(SimplifyIntersectLeft rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, false);
        var rightName = RuleChecks.table(builder, 1, false, false);
        RelNode left = builder.scan(leftName).build();
        RelNode right = builder.scan(rightName).build();
        RuleChecks.doesNotApply(rule, LogicalIntersect.create(List.of(left, right), false));
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyIntersectLeft test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("SimplifyIntersectLeft test failed");
        }
    }
}
