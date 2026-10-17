package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateExtractProject;
import org.qed.RuleBuilder;

public class AggregateExtractProjectTest {
    public static void runTest() {
        var rule = new AggregateExtractProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var a = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.table(builder, 1, false);
        var c = RuleChecks.table(builder, 1, false);
        var beforeBuilder = RuleChecks.ruleBuilder(builder);
        var before = beforeBuilder.scan(a).scan(b).join(org.apache.calcite.rel.core.JoinRelType.INNER,
                beforeBuilder.literal(true)).scan(c).join(org.apache.calcite.rel.core.JoinRelType.INNER,
                beforeBuilder.literal(true)).aggregate(beforeBuilder.groupKey(beforeBuilder.field(0)),
                beforeBuilder.aggregateCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.SUM,
                        false, null, "EXPR$0", beforeBuilder.field(1))).build();
        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        var expected = expectedBuilder.scan(a).scan(b).join(org.apache.calcite.rel.core.JoinRelType.INNER,
                expectedBuilder.literal(true)).scan(c).join(org.apache.calcite.rel.core.JoinRelType.INNER,
                expectedBuilder.literal(true)).project(expectedBuilder.field(0), expectedBuilder.field(1))
                .aggregate(expectedBuilder.groupKey(expectedBuilder.field(0)),
                        expectedBuilder.aggregateCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.SUM,
                                false, null, "EXPR$0", expectedBuilder.field(1))).build();
        RuleChecks.rewrites(rule, before, expected);

        var nearMissBuilder = RuleChecks.ruleBuilder(builder);
        var nearMiss = nearMissBuilder.scan(a).scan(b).join(org.apache.calcite.rel.core.JoinRelType.LEFT,
                nearMissBuilder.literal(true)).scan(c).join(org.apache.calcite.rel.core.JoinRelType.INNER,
                nearMissBuilder.literal(true)).aggregate(nearMissBuilder.groupKey(nearMissBuilder.field(0)),
                nearMissBuilder.aggregateCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.SUM,
                        false, null, "EXPR$0", nearMissBuilder.field(1))).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateExtractProject test...");
        runTest();
    }
}
