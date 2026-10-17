package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ReplaceIntersectWithSemiJoin;
import org.qed.RuleBuilder;

public class ReplaceIntersectWithSemiJoinTest {
    public static void runTest() {
        var rule = new ReplaceIntersectWithSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var fixture = RuleBuilder.create();
        var left = RuleChecks.table(fixture, 2, false);
        var right = RuleChecks.table(fixture, 2, false);
        var leftNode = fixture.scan(left).build();
        var rightNode = fixture.scan(right).build();
        var before = fixture.push(leftNode).push(rightNode).intersect(false, 2).build();

        var expectedBuilder = RuleChecks.ruleBuilder(fixture);
        expectedBuilder.push(leftNode).push(rightNode);
        var refs = new org.apache.calcite.rex.RexNode[] { CalciteRuntime.field(expectedBuilder, 2, 0),
                CalciteRuntime.field(expectedBuilder, 2, 2), CalciteRuntime.field(expectedBuilder, 2, 1),
                CalciteRuntime.field(expectedBuilder, 2, 3) };
        var eq0 = expectedBuilder.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, refs[0], refs[1]);
        var eq1 = expectedBuilder.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, refs[2], refs[3]);
        expectedBuilder.join(JoinRelType.SEMI, expectedBuilder.and(eq0, eq1));
        expectedBuilder.aggregate(expectedBuilder.groupKey(expectedBuilder.field(0), expectedBuilder.field(1)));
        var expected = expectedBuilder.build();
        RuleChecks.rewrites(rule, before, expected);

        var all = fixture.push(leftNode).push(rightNode).intersect(true, 2).build();
        RuleChecks.doesNotApply(rule, all);
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("Calcite rule test recorded failures");
        }
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceIntersectWithSemiJoin test...");
        runTest();
    }
}
