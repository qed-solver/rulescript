package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.CommuteRightJoin;
import org.qed.RuleBuilder;

public class CommuteRightJoinTest {
    public static void runTest() {
        var rule = new CommuteRightJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        concreteRewrite();
        rejectsLeftJoin();
    }

    private static void concreteRewrite() {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(left).scan(right);
        var condition = b.call(SqlStdOperatorTable.LESS_THAN,
                b.field(2, 0, 0), b.field(2, 1, 0));
        var before = b.join(JoinRelType.RIGHT, condition).build();

        var expectedBuilder = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedBuilder, 1, false);
        var expectedRight = RuleChecks.table(expectedBuilder, 1, false);
        var expected = RuleChecks.ruleBuilder(expectedBuilder);
        expected.scan(expectedRight).scan(expectedLeft);
        var expectedCondition = expected.call(SqlStdOperatorTable.LESS_THAN,
                expected.field(2, 1, 0), expected.field(2, 0, 0));
        var swappedJoin = expected.join(JoinRelType.LEFT, expectedCondition).build();
        var after = LogicalProject.create(swappedJoin, java.util.List.of(), java.util.List.of(
                new RexInputRef(1, swappedJoin.getRowType().getFieldList().get(1).getType()),
                new RexInputRef(0, swappedJoin.getRowType().getFieldList().get(0).getType())),
                java.util.List.of("Table_0_Column_0", "Table_1_Column_0"));

        RuleChecks.rewrites(new CommuteRightJoin(), before, after);
    }

    private static void rejectsLeftJoin() {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(left).scan(right);
        var condition = b.call(SqlStdOperatorTable.LESS_THAN,
                b.field(2, 0, 0), b.field(2, 1, 0));
        var nearMiss = b.join(JoinRelType.LEFT, condition).build();
        RuleChecks.doesNotApply(new CommuteRightJoin(), nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running CommuteRightJoin test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
