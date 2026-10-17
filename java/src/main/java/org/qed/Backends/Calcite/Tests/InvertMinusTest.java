package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.InvertMinus;
import org.qed.RuleBuilder;

public class InvertMinusTest {
    public static void runTest() {
        var rule = new InvertMinus();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var positive = RuleChecks.ruleBuilder(builder);
        positive.scan(table);
        positive.project(positive.call(SqlStdOperatorTable.UNARY_MINUS,
                positive.call(SqlStdOperatorTable.MINUS, positive.field(0), positive.field(1))));
        RelNode before = positive.build();
        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 2, false, false);
        var expectedPlanBuilder = expectedBuilder.transform(CalciteRuntime::configure);
        expectedPlanBuilder.scan(expectedTable);
        expectedPlanBuilder.project(expectedPlanBuilder.call(SqlStdOperatorTable.MINUS,
                expectedPlanBuilder.field(1), expectedPlanBuilder.field(0)));
        RelNode expected = expectedPlanBuilder.build();
        RuleChecks.rewrites(rule, before, expected);

        var nearBuilder = RuleBuilder.create();
        var nearTable = RuleChecks.table(nearBuilder, 2, false, false);
        var nearPlanBuilder = nearBuilder.transform(CalciteRuntime::configure);
        nearPlanBuilder.scan(nearTable);
        nearPlanBuilder.project(nearPlanBuilder.call(SqlStdOperatorTable.UNARY_MINUS,
                nearPlanBuilder.call(SqlStdOperatorTable.MINUS, nearPlanBuilder.field(0), nearPlanBuilder.literal(1))));
        RelNode nearMiss = nearPlanBuilder.build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running InvertMinus test...");
        runTest();
    }
}
