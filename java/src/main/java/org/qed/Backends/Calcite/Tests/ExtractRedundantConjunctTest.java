package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ExtractRedundantConjunct;
import org.qed.RuleBuilder;

public class ExtractRedundantConjunctTest {
    public static void runTest() {
        var rule = new ExtractRedundantConjunct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive();
        handBuiltNoMatch();
    }

    private static void handBuiltPositive() {
        var beforeSetup = builderWithOneColumn();
        var beforeBuilder = beforeSetup.builder();
        var beforeInput = beforeBuilder.scan(beforeSetup.table()).build();
        var beforeX = beforeBuilder.getRexBuilder().makeInputRef(beforeInput, 0);
        var common = RuleChecks.gt(beforeBuilder, beforeX, 10);
        var left = beforeBuilder.call(SqlStdOperatorTable.LESS_THAN, beforeX, beforeBuilder.literal(20));
        var right = beforeBuilder.call(SqlStdOperatorTable.EQUALS, beforeX, beforeBuilder.literal(15));
        var beforeCondition = beforeBuilder.call(SqlStdOperatorTable.OR,
                beforeBuilder.call(SqlStdOperatorTable.AND, common, left),
                beforeBuilder.call(SqlStdOperatorTable.AND, common, right));
        RelNode before = org.apache.calcite.rel.logical.LogicalFilter.create(beforeInput, beforeCondition);

        var expectedSetup = builderWithOneColumn();
        var expectedBuilder = expectedSetup.builder();
        var expectedInput = expectedBuilder.scan(expectedSetup.table()).build();
        var expectedX = expectedBuilder.getRexBuilder().makeInputRef(expectedInput, 0);
        
        var expectedCondition = expectedBuilder.call(SqlStdOperatorTable.AND,
                RuleChecks.gt(expectedBuilder, expectedX, 10),
                expectedBuilder.call(SqlStdOperatorTable.OR,
                        expectedBuilder.call(SqlStdOperatorTable.LESS_THAN, expectedX, expectedBuilder.literal(20)),
                        expectedBuilder.call(SqlStdOperatorTable.EQUALS, expectedX, expectedBuilder.literal(15))));
        RelNode expected = org.apache.calcite.rel.logical.LogicalFilter.create(expectedInput, expectedCondition);
        RuleChecks.rewrites(new ExtractRedundantConjunct(), before, expected);
    }

    private static void handBuiltNoMatch() {
        var setup = builderWithOneColumn();
        var b = setup.builder();
        var input = b.scan(setup.table()).build();
        var x = b.getRexBuilder().makeInputRef(input, 0);
        var first = b.call(SqlStdOperatorTable.AND, RuleChecks.gt(b, x, 10),
                b.call(SqlStdOperatorTable.LESS_THAN, x, b.literal(20)));
        var second = b.call(SqlStdOperatorTable.AND, b.call(SqlStdOperatorTable.EQUALS, x, b.literal(15)),
                b.call(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, x, b.literal(30)));
        var condition = b.call(SqlStdOperatorTable.OR, first, second);
        RelNode plan = org.apache.calcite.rel.logical.LogicalFilter.create(input, condition);
        RuleChecks.doesNotApply(new ExtractRedundantConjunct(), plan);
    }

    private static BuilderSetup builderWithOneColumn() {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        return new BuilderSetup(RuleChecks.ruleBuilder(ruleBuilder), table);
    }

    private record BuilderSetup(RelBuilder builder, String table) {}

    public static void main(String[] args) {
        System.out.println("Running ExtractRedundantConjunct test...");
        runTest();
    }
}
