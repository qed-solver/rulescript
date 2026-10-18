package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRule;
import org.qed.RRuleInstances.MapFilterIntoJoinRight;
import org.qed.RuleBuilder;

public class MapFilterIntoJoinRightTest {
    public static void runTest() {
        var rule = new MapFilterIntoJoinRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false, false);
        var rightName = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var input = b.push(left).push(right).join(JoinRelType.INNER,
                b.call(SqlStdOperatorTable.AND,
                        RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                        RuleChecks.gt(b, b.field(2, 0, 0), 7))).build();

        var expectedBuilder = RuleBuilder.create();
        var expectedLeftName = RuleChecks.table(expectedBuilder, 1, false, false);
        var expectedRightName = RuleChecks.table(expectedBuilder, 1, false, false);
        var expected = RuleChecks.ruleBuilder(expectedBuilder);
        var expectedPlan = expected.push(expected.scan(expectedLeftName).build())
                .push(expected.scan(expectedRightName).build())
                .join(JoinRelType.INNER, expected.call(SqlStdOperatorTable.AND,
                        RuleChecks.eq(expected, expected.field(2, 0, 0), expected.field(2, 1, 0)),
                        RuleChecks.gt(expected, expected.field(2, 1, 0), 7)))
                .build();

        var planner = org.qed.Backends.Calcite.CalciteTester.loadRule(
                RuleChecks.generated(rule.name()), 1);
        planner.setRoot(input);
        var actual = planner.findBestExp();
        if (!(actual instanceof LogicalJoin join) || !actual.deepEquals(expectedPlan)) {
            org.qed.Backends.Calcite.CalciteTester.fail(rule.name()
                    + " produced an unexpected ordinary-table rewrite\nActual:\n" + actual.explain()
                    + "\nExpected:\n" + expectedPlan.explain());
        } else {
            System.out.println("succeeded (ordinary tables remap the filter to the right key)");
        }

        var nearBuilder = RuleBuilder.create();
        var nearLeftName = RuleChecks.table(nearBuilder, 1, false, false);
        var nearRightName = RuleChecks.table(nearBuilder, 1, false, false);
        var near = RuleChecks.ruleBuilder(nearBuilder);
        var nearInput = near.push(near.scan(nearLeftName).build()).push(near.scan(nearRightName).build())
                .join(JoinRelType.LEFT, near.call(SqlStdOperatorTable.AND,
                        RuleChecks.eq(near, near.field(2, 0, 0), near.field(2, 1, 0)),
                        RuleChecks.gt(near, near.field(2, 0, 0), 7))).build();
        RuleChecks.doesNotApply(rule, nearInput);
    }

    public static void main(String[] args) {
        System.out.println("Running MapFilterIntoJoinRight test...");
        runTest();
    }
}
