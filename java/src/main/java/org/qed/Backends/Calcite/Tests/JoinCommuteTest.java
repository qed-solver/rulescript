package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.JoinCommute;
import org.qed.RuleBuilder;

public class JoinCommuteTest {
    public static void runTest() {
        var rule = new JoinCommute();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltJoinRewrites();
        rejectsOuterJoin();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("JoinCommute checks failed");
        }
    }

    private static void handBuiltJoinRewrites() {
        RuleBuilder builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var rel = builder.scan(left).scan(right)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS,
                        builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleBuilder expectedBuilder = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedBuilder, 1, false);
        var expectedRight = RuleChecks.table(expectedBuilder, 1, false);
        var expected = expectedBuilder.scan(expectedRight).scan(expectedLeft)
                .join(JoinRelType.INNER, expectedBuilder.call(SqlStdOperatorTable.EQUALS,
                        expectedBuilder.field(2, 0, 0), expectedBuilder.field(2, 1, 0)))
                .project(expectedBuilder.fields(java.util.List.of(1, 0))).build();
        RuleChecks.rewrites(rule(), rel, expected);
    }

    private static void rejectsOuterJoin() {
        RuleBuilder builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 2, false);
        var right = RuleChecks.table(builder, 2, false);
        RelNode plan = builder.scan(left).scan(right)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.EQUALS,
                        builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule(), plan);
    }

    private static JoinCommute rule() {
        return new JoinCommute();
    }

    public static void main(String[] args) {
        System.out.println("Running JoinCommute test...");
        runTest();
    }
}
