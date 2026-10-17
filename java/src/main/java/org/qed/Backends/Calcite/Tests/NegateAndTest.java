package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteTester;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NegateAnd;
import org.qed.RuleBuilder;

public class NegateAndTest {
    public static void runTest() {
        var rule = new NegateAnd();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NegateAnd rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var input = builder.scan(table);
        var predicate = RuleBuilder.create().genericPredicateOp("predicate", true);
        var left = builder.call(predicate, input.field(0), builder.literal(1));
        var right = builder.call(predicate, input.field(0), builder.literal(2));
        var beforeInput = builder.build();
        var beforeRex = builder.getRexBuilder();
        var before = LogicalFilter.create(beforeInput, beforeRex.makeCall(SqlStdOperatorTable.NOT,
                beforeRex.makeCall(SqlStdOperatorTable.AND, left, right)));

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false);
        var expectedInput = expectedBuilder.scan(expectedTable);
        var expectedPredicate = RuleBuilder.create().genericPredicateOp("predicate", true);
        var expectedLeft = expectedBuilder.call(expectedPredicate, expectedInput.field(0), expectedBuilder.literal(1));
        var expectedRight = expectedBuilder.call(expectedPredicate, expectedInput.field(0), expectedBuilder.literal(2));
        var expectedRel = expectedBuilder.build();
        var expectedRex = expectedBuilder.getRexBuilder();
        var after = LogicalFilter.create(expectedRel, expectedRex.makeCall(SqlStdOperatorTable.OR,
                expectedRex.makeCall(SqlStdOperatorTable.NOT, expectedLeft),
                expectedRex.makeCall(SqlStdOperatorTable.NOT, expectedRight)));
        RuleChecks.rewrites(rule, before, after);

        var nearBuilder = RuleBuilder.create();
        var nearTable = RuleChecks.table(nearBuilder, 1, false);
        var nearInput = nearBuilder.scan(nearTable);
        var nearPredicate = RuleBuilder.create().genericPredicateOp("predicate", true);
        var nearLeft = nearBuilder.call(nearPredicate, nearInput.field(0), nearBuilder.literal(1));
        var nearRight = nearBuilder.call(nearPredicate, nearInput.field(0), nearBuilder.literal(2));
        var nearInputPlan = nearBuilder.build();
        var nearRex = nearBuilder.getRexBuilder();
        var nearMiss = LogicalFilter.create(nearInputPlan, nearRex.makeCall(SqlStdOperatorTable.NOT,
                nearRex.makeCall(SqlStdOperatorTable.OR, nearLeft, nearRight)));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running NegateAnd test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError(CalciteTester.failures() + " NegateAnd test failure(s)");
        }
    }
}
