package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldUnary;
import org.qed.RuleBuilder;

public final class FoldUnaryTest {
    public static void runTest() {
        var rule = new FoldUnary();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var sourceRules = RuleBuilder.create();
        var table = RuleChecks.table(sourceRules, 1, false);
        var builder = RuleChecks.ruleBuilder(sourceRules);
        var input = builder.scan(table).build();
        var notTrue = builder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT,
                builder.getRexBuilder().makeLiteral(true));
        RelNode before = LogicalFilter.create(input, notTrue);

        var expectedRules = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedRules, 1, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedRules);
        var expectedInput = expectedBuilder.scan(expectedTable).build();
        RelNode expected = LogicalFilter.create(expectedInput, expectedBuilder.getRexBuilder().makeLiteral(false));
        RuleChecks.rewrites(rule, before, expected);

        var missRules = RuleBuilder.create();
        var missTable = RuleChecks.table(missRules, 1, false);
        var missBuilder = RuleChecks.ruleBuilder(missRules);
        var missInput = missBuilder.scan(missTable).build();
        var notFalse = missBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT,
                missBuilder.getRexBuilder().makeLiteral(false));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(missInput, notFalse));
    }

    public static void main(String[] args) {
        System.out.println("Running FoldUnary test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
