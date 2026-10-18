package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.RemoveLiteralFromGroupExpressions;
import org.qed.RuleBuilder;

import java.util.List;

public class RemoveLiteralFromGroupExpressionsTest {
    public static void runTest() {
        var rule = new RemoveLiteralFromGroupExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RemoveLiteralFromGroupExpressions rule) {
        // The constant group expression is an explicit zero-argument call; the
        // aggregate input projects the two source columns around it.
        var beforeRules = RuleBuilder.create();
        var beforeTable = RuleChecks.table(beforeRules, 2, false, false);
        var beforeBuilder = RuleChecks.ruleBuilder(beforeRules);
        beforeBuilder.scan(beforeTable);
        var beforeRex = beforeBuilder.getRexBuilder();
        var beforePi = beforeRex.makeCall(SqlStdOperatorTable.PI);
        beforeBuilder.project(List.of(beforeBuilder.field(0), beforePi, beforeBuilder.field(1)));
        beforeBuilder.aggregate(beforeBuilder.groupKey(0, 1),
                beforeBuilder.aggregateCall(SqlStdOperatorTable.SUM, false, null, "sum",
                        List.of(beforeBuilder.field(2))),
                beforeBuilder.aggregateCall(SqlStdOperatorTable.MAX, false, null, "max",
                        List.of(beforeBuilder.field(2))));
        var before = beforeBuilder.build();

        var expectedRules = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedRules, 2, false, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedRules);
        expectedBuilder.scan(expectedTable);
        expectedBuilder.aggregate(expectedBuilder.groupKey(0),
                expectedBuilder.aggregateCall(SqlStdOperatorTable.SUM, false, null, "sum",
                        List.of(expectedBuilder.field(1))),
                expectedBuilder.aggregateCall(SqlStdOperatorTable.MAX, false, null, "max",
                        List.of(expectedBuilder.field(1))));
        var grouped = expectedBuilder.build();
        var expected = expectedBuilder.push(grouped)
                .project(List.of(expectedBuilder.field(0), expectedBuilder.getRexBuilder()
                                .makeCall(SqlStdOperatorTable.PI), expectedBuilder.field(1), expectedBuilder.field(2)))
                .build();
        RuleChecks.rewrites(rule, before, expected);

        var nearMissRules = RuleBuilder.create();
        var nearMissTable = RuleChecks.table(nearMissRules, 2, false, false);
        var nearMissBuilder = RuleChecks.ruleBuilder(nearMissRules);
        nearMissBuilder.scan(nearMissTable);
        var nearMissPi = nearMissBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.PI);
        nearMissBuilder.project(List.of(nearMissBuilder.field(0), nearMissPi, nearMissBuilder.field(1)));
        nearMissBuilder.aggregate(nearMissBuilder.groupKey(0, 1),
                nearMissBuilder.aggregateCall(SqlStdOperatorTable.SUM, true, null, "sum",
                        List.of(nearMissBuilder.field(2))),
                nearMissBuilder.aggregateCall(SqlStdOperatorTable.MAX, false, null, "max",
                        List.of(nearMissBuilder.field(2))));
        RuleChecks.doesNotApply(rule, nearMissBuilder.build());
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveLiteralFromGroupExpressions test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError(CalciteTester.failures() + " test failures");
        }
    }
}
