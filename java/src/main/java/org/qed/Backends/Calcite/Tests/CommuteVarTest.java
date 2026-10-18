package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.CommuteVar;
import org.qed.RuleBuilder;

public final class CommuteVarTest {
    public static void runTest() {
        var rule = new CommuteVar();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesConcreteExpression(rule);
        rejectsReversedOperands(rule);
        rejectsWiderInput(rule);
    }

    private static void rewritesConcreteExpression(CommuteVar rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        relBuilder.scan(table);
        var input = relBuilder.build();
        relBuilder.push(input);
        var x = relBuilder.field(0);
        var e = relBuilder.call(SqlStdOperatorTable.PLUS, x, relBuilder.literal(1));
        var before = LogicalFilter.create(input,
                relBuilder.call(SqlStdOperatorTable.EQUALS, e, x));
        var expected = LogicalFilter.create(input,
                relBuilder.call(SqlStdOperatorTable.EQUALS, x, e));
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsReversedOperands(CommuteVar rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        relBuilder.scan(table);
        var input = relBuilder.build();
        relBuilder.push(input);
        var x = relBuilder.field(0);
        var e = relBuilder.call(SqlStdOperatorTable.PLUS, x, relBuilder.literal(1));
        var reversed = LogicalFilter.create(input,
                relBuilder.call(SqlStdOperatorTable.EQUALS, x, e));
        RuleChecks.doesNotApply(rule, reversed);
    }

    private static void rejectsWiderInput(CommuteVar rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        relBuilder.scan(table);
        var input = relBuilder.build();
        relBuilder.push(input);
        var x = relBuilder.field(0);
        var e = relBuilder.call(SqlStdOperatorTable.PLUS, x, relBuilder.literal(1));
        var nearMiss = LogicalFilter.create(input,
                relBuilder.call(SqlStdOperatorTable.EQUALS, e, x));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running CommuteVar test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("CommuteVar test had " + CalciteTester.failures() + " failure(s)");
        }
    }
}
