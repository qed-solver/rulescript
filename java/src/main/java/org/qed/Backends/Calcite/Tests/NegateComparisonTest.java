package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.NegateComparison;
import org.qed.RuleBuilder;

public class NegateComparisonTest {
    public static void runTest() {
        var rule = new NegateComparison();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NegateComparison rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false, false);
        var r = RuleChecks.table(builder, 1, false, false);
        var base = builder.scan(l).scan(r).join(JoinRelType.INNER, builder.literal(true)).build();
        var rexBuilder = base.getCluster().getRexBuilder();
        var left = rexBuilder.makeInputRef(base.getRowType().getFieldList().get(0).getType(), 0);
        var right = rexBuilder.makeInputRef(base.getRowType().getFieldList().get(1).getType(), 1);
        var beforeCondition = rexBuilder.makeCall(SqlStdOperatorTable.NOT,
                rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, left, right));
        var before = LogicalFilter.create(base, beforeCondition);
        var plain = RuleChecks.ruleBuilder(builder);
        var expectedBase = plain.scan(l).scan(r).join(JoinRelType.INNER, plain.literal(true)).build();
        var expectedRexBuilder = expectedBase.getCluster().getRexBuilder();
        var expectedCondition = expectedRexBuilder.makeCall(SqlStdOperatorTable.NOT_EQUALS,
                expectedRexBuilder.makeInputRef(expectedBase.getRowType().getFieldList().get(0).getType(), 0),
                expectedRexBuilder.makeInputRef(expectedBase.getRowType().getFieldList().get(1).getType(), 1));
        var expected = LogicalFilter.create(expectedBase, expectedCondition);
        RuleChecks.rewrites(rule, before, expected);

        var wrongCondition = rexBuilder.makeCall(SqlStdOperatorTable.NOT,
                rexBuilder.makeCall(SqlStdOperatorTable.GREATER_THAN, left, right));
        var wrong = LogicalFilter.create(base, wrongCondition);
        RuleChecks.doesNotApply(rule, wrong);
    }

    public static void main(String[] args) {
        System.out.println("Running NegateComparison test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
