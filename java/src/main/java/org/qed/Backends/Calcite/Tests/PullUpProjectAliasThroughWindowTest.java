package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RuleBuilder;

public class PullUpProjectAliasThroughWindowTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.PullUpProjectAliasThroughWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.qed.RRuleInstances.PullUpProjectAliasThroughWindow rule) {
        var root = RuleBuilder.create();
        var table = table(root, false);
        var builder = root.transform(CalciteRuntime::configure);
        var input = builder.scan(table).build();
        builder.push(input);
        var alias = builder.call(SqlStdOperatorTable.ABS, builder.field(1));
        var lower = builder.project(java.util.List.of(builder.field(0), builder.field(1), alias),
                java.util.List.of(), true).build();
        var aggregate = aggregate(builder, lower);
        var before = window(builder, lower, aggregate, true);
        var expectedBuilder = RuleChecks.ruleBuilder(root);
        var expected = expected(expectedBuilder, table);
        RuleChecks.rewrites(rule, before, expected);

        var nearMissRoot = RuleBuilder.create();
        var nearMissTable = table(nearMissRoot, true);
        var nearMissBuilder = nearMissRoot.transform(CalciteRuntime::configure);
        var nullableInput = nearMissBuilder.scan(nearMissTable).build();
        var nullableLower = nearMissBuilder.push(nullableInput).project(java.util.List.of(
                nearMissBuilder.field(0), nearMissBuilder.field(1),
                nearMissBuilder.call(SqlStdOperatorTable.ABS, nearMissBuilder.field(1))),
                java.util.List.of(), true).build();
        var nullableAggregate = aggregate(nearMissBuilder, nullableLower);
        var nearMiss = window(nearMissBuilder, nullableLower, nullableAggregate, true);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static RelNode aggregate(RelBuilder builder, RelNode lower) {
        builder.push(lower);
        var call = builder.aggregateCall(new RuleBuilder.QedAggregateFunction("w",
                        org.qed.RelType.fromString("INTEGER", true)), false, null, "w",
                java.util.List.of(builder.field(1)));
        return builder.aggregate(builder.groupKey(builder.field(0)), java.util.List.of(call)).build();
    }

    private static RelNode window(RelBuilder builder, RelNode lower, RelNode aggregate, boolean identityTop) {
        var joined = builder.push(lower).push(aggregate).join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        var window = builder.push(joined).project(java.util.List.of(builder.field(0), builder.field(1),
                builder.field(2), builder.field(4)), java.util.List.of(), true).build();
        var result = builder.push(window);
        if (identityTop) {
            return result.project(java.util.List.of(builder.field(0), builder.field(1), builder.field(2),
                    builder.field(3)), java.util.List.of(), true).build();
        }
        return result.build();
    }

    private static RelNode expected(RelBuilder builder, String table) {
        var input = builder.scan(table).build();
        var lower = input;
        builder.push(lower);
        var call = builder.aggregateCall(new RuleBuilder.QedAggregateFunction("w",
                        org.qed.RelType.fromString("INTEGER", true)), false, null, "w",
                java.util.List.of(builder.field(1)));
        var aggregate = builder.aggregate(builder.groupKey(builder.field(0)), java.util.List.of(call)).build();
        var joined = builder.push(lower).push(aggregate).join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        var window = builder.push(joined).project(java.util.List.of(builder.field(0), builder.field(1),
                builder.field(3)), java.util.List.of(), true).build();
        return builder.push(window).project(java.util.List.of(builder.field(0), builder.field(1),
                builder.call(SqlStdOperatorTable.ABS, builder.field(1)), builder.field(2)), java.util.List.of(), true).build();
    }

    private static String table(RuleBuilder builder, boolean nullableKey) {
        var table = builder.createQedTable(kala.collection.Seq.of(
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", nullableKey), false),
                kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false)));
        builder.addTable(table);
        return table.getName();
    }

    public static void main(String[] args) {
        System.out.println("Running PullUpProjectAliasThroughWindow test...");
        runTest();
    }
}
