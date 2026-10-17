package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.FilterWindowTranspose;
import org.qed.RuleBuilder;


public final class FilterWindowTransposeTest {
    public static void runTest() {
        var rule = new FilterWindowTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var ruleBuilder = RuleBuilder.create();
        var scan = table(ruleBuilder, "T", 2);
        var builder = ruleBuilder.transform(config -> CalciteRuntime.configure(config)
                .withSimplifyValues(false).withAggregateUnique(false).withDedupAggregateCalls(false)
                .withRemoveRedundantDistinct(false));
        var base = builder.scan(scan).build();
        builder.push(base);
        var key = builder.field(0);
        var value = builder.field(1);
        var keyPredicate = builder.call(SqlStdOperatorTable.GREATER_THAN, key, builder.literal(10));
        var aggregate = builder.aggregate(builder.groupKey(key),
                builder.aggregateCall(SqlStdOperatorTable.SUM, value).as("total")).build();
        builder.push(aggregate);
        var condition = builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(1), builder.literal(100));
        var input = LogicalFilter.create(aggregate,
                builder.call(SqlStdOperatorTable.AND, keyPredicate, condition));

        var expectedBase = LogicalFilter.create(base, keyPredicate);
        builder.push(expectedBase);
        var expectedAggregate = builder.aggregate(builder.groupKey(builder.field(0)),
                builder.aggregateCall(SqlStdOperatorTable.SUM, builder.field(1)).as("total")).build();
        builder.push(expectedAggregate);
        var expected = LogicalFilter.create(expectedAggregate,
                builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(1), builder.literal(100)));
        RuleChecks.rewrites(rule, input, expected);

        builder.push(aggregate);
        var misplacedKeyPredicate = builder.call(SqlStdOperatorTable.GREATER_THAN,
                builder.field(0), builder.literal(10));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(aggregate,
                builder.call(SqlStdOperatorTable.AND, condition, misplacedKeyPredicate)));
    }

    private static RelBuilder builder() {
        return RuleBuilder.create().transform(config -> CalciteRuntime.configure(config)
                .withSimplifyValues(false).withAggregateUnique(false).withDedupAggregateCalls(false)
                .withRemoveRedundantDistinct(false));
    }

    private static String table(RuleBuilder ruleBuilder, String name, int columns) {
        var schema = new java.util.ArrayList<kala.tuple.Tuple2<org.qed.RelType, Boolean>>();
        for (var i = 0; i < columns; i++) {
            schema.add(kala.tuple.Tuple.of(org.qed.RelType.fromString("INTEGER", true), false));
        }
        var table = new org.qed.QedTable(name,
                kala.collection.Seq.from(java.util.stream.IntStream.range(0, columns)
                        .mapToObj(i -> "c" + i).toList()),
                kala.collection.Seq.from(java.util.stream.IntStream.range(0, columns)
                        .mapToObj(i -> org.qed.RelType.fromString("INTEGER", true)).toList()),
                kala.collection.Set.empty(), kala.collection.Set.empty());
        ruleBuilder.addTable(table);
        return table.getName();
    }

    public static void main(String[] args) {
        System.out.println("Running FilterWindowTranspose test...");
        runTest();
    }
}
