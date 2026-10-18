package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.Tests.RuleChecks;
import org.qed.RRuleInstances.ColumnPruning;
import org.qed.RuleBuilder;

import java.util.List;

public final class ColumnPruningTest {
    public static void runTest() {
        var rule = new ColumnPruning();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 3, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        var before = before(relBuilder, table, false);
        var expected = after(relBuilder, table);
        rewrites(rule, before, expected);
        doesNotApply(rule, before(relBuilder, table, true));
    }

    private static RelNode before(RelBuilder builder, String table, boolean nearMiss) {
        var scan = builder.scan(table).build();
        builder.push(scan);
        var inner = builder.project(List.of(
                plus(builder, builder.field(0), 1),
                plus(builder, builder.field(1), 2),
                plus(builder, builder.field(2), 3),
                plus(builder, builder.field(0), 4)), List.of(), true).build();
        builder.push(inner);
        var second = builder.field(nearMiss ? 1 : 2);
        return builder.project(List.of(
                multiply(builder, builder.field(0), 5),
                multiply(builder, second, 6)), List.of(), true).build();
    }

    private static RelNode after(RelBuilder builder, String table) {
        var scan = builder.scan(table).build();
        builder.push(scan);
        var inner = builder.project(List.of(
                plus(builder, builder.field(0), 1),
                plus(builder, builder.field(2), 3)), List.of(), true).build();
        builder.push(inner);
        return builder.project(List.of(
                multiply(builder, builder.field(0), 5),
                multiply(builder, builder.field(1), 6)), List.of(), true).build();
    }

    private static RexNode plus(RelBuilder builder, RexNode value, int constant) {
        return builder.call(SqlStdOperatorTable.PLUS, value, builder.literal(constant));
    }

    private static RexNode multiply(RelBuilder builder, RexNode value, int constant) {
        return builder.call(SqlStdOperatorTable.MULTIPLY, value, builder.literal(constant));
    }

    private static void rewrites(ColumnPruning rule, RelNode before, RelNode expected) {
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void doesNotApply(ColumnPruning rule, RelNode plan) {
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running ColumnPruning test...");
        runTest();
    }
}
