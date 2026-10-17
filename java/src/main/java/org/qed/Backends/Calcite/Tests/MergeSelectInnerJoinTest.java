package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.MergeSelectInnerJoin;

public class MergeSelectInnerJoinTest {
    public static void runTest() {
        var rule = new MergeSelectInnerJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MergeSelectInnerJoin rule) {
        var builder = org.qed.RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(left).scan(right);
        var on = RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0));
        var join = b.join(JoinRelType.INNER, on).build();
        var rexBuilder = join.getCluster().getRexBuilder();
        var leftRef = rexBuilder.makeInputRef(join.getRowType().getFieldList().get(0).getType(), 0);
        var rightRef = rexBuilder.makeInputRef(join.getRowType().getFieldList().get(1).getType(), 1);
        var above = rexBuilder.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                RuleChecks.gt(b, leftRef, 10), RuleChecks.gt(b, rightRef, 20));
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(join, above);
        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        expectedBuilder.scan(left).scan(right);
        var expectedOn = RuleChecks.eq(expectedBuilder, expectedBuilder.field(2, 0, 0), expectedBuilder.field(2, 1, 0));
        var expectedAbove = expectedBuilder.and(
                RuleChecks.gt(expectedBuilder, expectedBuilder.field(2, 0, 0), 10),
                RuleChecks.gt(expectedBuilder, expectedBuilder.field(2, 1, 0), 20));
        var expected = expectedBuilder.join(JoinRelType.INNER, expectedBuilder.and(expectedOn, expectedAbove)).build();
        var planner = CalciteTester.loadRule(RuleChecks.generated(rule.name()), 1);
        planner.setRoot(before);
        var actual = planner.findBestExp();
        if (!actual.deepEquals(expected)) {
            CalciteTester.fail(rule.name() + " ordinary table rewrite mismatch\nactual:\n" + actual.explain()
                    + "\nexpected:\n" + expected.explain());
        } else {
            System.out.println("succeeded (ordinary tables)");
        }

        var nonInnerBuilder = RuleChecks.ruleBuilder(builder);
        nonInnerBuilder.scan(left).scan(right);
        var nonInnerJoin = nonInnerBuilder.join(JoinRelType.LEFT,
                RuleChecks.eq(nonInnerBuilder, nonInnerBuilder.field(2, 0, 0), nonInnerBuilder.field(2, 1, 0))).build();
        var nonInnerRefs = nonInnerJoin.getCluster().getRexBuilder();
        var nonInnerAbove = nonInnerRefs.makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.AND,
                RuleChecks.gt(nonInnerBuilder, nonInnerRefs.makeInputRef(nonInnerJoin.getRowType().getFieldList().get(0).getType(), 0), 10),
                RuleChecks.gt(nonInnerBuilder, nonInnerRefs.makeInputRef(nonInnerJoin.getRowType().getFieldList().get(1).getType(), 1), 20));
        RelNode nonInner = org.apache.calcite.rel.logical.LogicalFilter.create(nonInnerJoin, nonInnerAbove);
        RuleChecks.doesNotApply(rule, nonInner);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
