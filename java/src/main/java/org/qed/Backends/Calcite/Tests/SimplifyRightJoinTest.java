package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyRightJoin;
import org.qed.RuleBuilder;

public class SimplifyRightJoinTest {
    public static void runTest() {
        var rule = new SimplifyRightJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var beforeBuilder = builder();
        var table = RuleChecks.table(beforeBuilder, 1, false, false);
        var before = beforeBuilder.scan(table).scan(table).join(JoinRelType.FULL,
                beforeBuilder.call(SqlStdOperatorTable.EQUALS,
                        beforeBuilder.field(2, 0, 0), beforeBuilder.field(2, 1, 0))).build();

        var expectedBuilder = builder();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false, false);
        var expected = expectedBuilder.scan(expectedTable).scan(expectedTable).join(JoinRelType.LEFT,
                expectedBuilder.call(SqlStdOperatorTable.EQUALS,
                        expectedBuilder.field(2, 0, 0), expectedBuilder.field(2, 1, 0))).build();
        RuleChecks.rewrites(rule, before, expected);

        var nearMissBuilder = builder();
        var left = RuleChecks.table(nearMissBuilder, 1, false, false);
        var right = RuleChecks.table(nearMissBuilder, 1, false, false);
        var nearMiss = nearMissBuilder.scan(left).scan(right).join(JoinRelType.FULL,
                nearMissBuilder.call(SqlStdOperatorTable.EQUALS,
                        nearMissBuilder.field(2, 0, 0), nearMissBuilder.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RuleBuilder builder() {
        return RuleBuilder.create();
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyRightJoin test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("SimplifyRightJoin test had " + CalciteTester.failures() + " failure(s)");
        }
    }
}
