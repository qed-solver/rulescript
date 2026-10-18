package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldComparison;
import org.qed.RuleBuilder;

public final class FoldComparisonTest {
    public static void runTest() {
        var rule = new FoldComparison();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rejectsFalseEqualsTrue();
        rejectsInequality();
        rejectsProjection();
    }

    private static void rejectsFalseEqualsTrue() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 0, false);
        var before = builder.scan(table).filter(builder.call(SqlStdOperatorTable.EQUALS,
                builder.literal(false), builder.literal(true))).build();
        RuleChecks.doesNotApply(new FoldComparison(), before);
    }

    private static void rejectsInequality() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 0, false);
        var before = builder.scan(table).filter(builder.call(SqlStdOperatorTable.NOT_EQUALS,
                builder.literal(true), builder.literal(false))).build();
        RuleChecks.doesNotApply(new FoldComparison(), before);
    }

    private static void rejectsProjection() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 0, false);
        RelNode input = builder.scan(table).build();
        var rexBuilder = input.getCluster().getRexBuilder();
        var comparison = rexBuilder.makeCall(SqlStdOperatorTable.EQUALS,
                rexBuilder.makeLiteral(true), rexBuilder.makeLiteral(false));
        var projection = org.apache.calcite.rel.logical.LogicalProject.create(input, java.util.List.of(),
                java.util.List.of(comparison), java.util.List.of("comparison"));
        RuleChecks.doesNotApply(new FoldComparison(), projection);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldComparison test...");
        runTest();
    }
}
