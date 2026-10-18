package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.JoinPushTransitivePredicates;

public class JoinPushTransitivePredicatesTest {
    public static void runTest() {
        var rule = new JoinPushTransitivePredicates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinPushTransitivePredicates rule) {
        var builder = org.qed.RuleBuilder.create();
        var leftTable = RuleChecks.table(builder, 1, false, false);
        var rightTable = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftTable).build();
        var right = b.scan(rightTable).filter(RuleChecks.gt(b, b.field(0), 7)).build();
        var before = b.push(left).push(right)
                .join(JoinRelType.INNER, joinCondition(b))
                .build();

        var expectedBuilder = org.qed.RuleBuilder.create();
        var expectedLeftTable = RuleChecks.table(expectedBuilder, 1, false, false);
        var expectedRightTable = RuleChecks.table(expectedBuilder, 1, false, false);
        var e = RuleChecks.ruleBuilder(expectedBuilder);
        var expectedLeft = e.scan(expectedLeftTable).filter(RuleChecks.gt(e, e.field(0), 7)).build();
        var expectedRight = e.scan(expectedRightTable).filter(RuleChecks.gt(e, e.field(0), 7)).build();
        var expected = e.push(expectedLeft).push(expectedRight)
                .join(JoinRelType.INNER, joinCondition(e))
                .build();
        RuleChecks.rewrites(rule, before, expected);

        var missBuilder = org.qed.RuleBuilder.create();
        var missLeftTable = RuleChecks.table(missBuilder, 1, false, false);
        var missRightTable = RuleChecks.table(missBuilder, 1, false, false);
        var m = RuleChecks.ruleBuilder(missBuilder);
        var nearMiss = m.scan(missLeftTable).scan(missRightTable)
                .join(JoinRelType.LEFT, joinCondition(m))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static org.apache.calcite.rex.RexNode joinCondition(org.apache.calcite.tools.RelBuilder b) {
        var rexBuilder = b.getRexBuilder();
        var left = rexBuilder.makeInputRef(b.peek(1).getRowType(), 0);
        var right = rexBuilder.makeInputRef(b.peek(0).getRowType(), 1);
        return rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS, left, right);
    }

    public static void main(String[] args) {
        System.out.println("Running JoinPushTransitivePredicates test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("JoinPushTransitivePredicates test failed");
        }
    }
}
