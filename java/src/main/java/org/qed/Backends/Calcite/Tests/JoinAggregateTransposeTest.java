package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.JoinAggregateTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public final class JoinAggregateTransposeTest {
    public static void runTest() {
        var rule = new JoinAggregateTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltUniqueJoin();
        rejectsHandBuiltNonUniqueJoin();
    }

    private static void rewritesHandBuiltUniqueJoin() {
        var schema = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(schema);
        var leftName = RuleChecks.table(schema, 2, false);
        var rightName = RuleChecks.table(schema, 1, true);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var before = builder.push(left)
                .aggregate(builder.groupKey(builder.field(0)), builder.aggregateCall(SqlStdOperatorTable.SUM, false, null, "SUM_V", List.of(builder.field(1))))
                .build();
        var beforeJoin = builder.push(before).push(right)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        var expected = builder.push(left).push(right)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .aggregate(builder.groupKey(builder.field(0), builder.field(2)), builder.aggregateCall(SqlStdOperatorTable.SUM, false, null, "SUM_V", List.of(builder.field(1))))
                .project(List.of(builder.field(0), builder.field(2), builder.field(1)))
                .build();
        RuleChecks.rewrites(new JoinAggregateTranspose(), beforeJoin, expected);
    }

    private static void rejectsHandBuiltNonUniqueJoin() {
        var schema = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(schema);
        var leftName = RuleChecks.table(schema, 2, false);
        var rightName = RuleChecks.table(schema, 1, false);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var plan = builder.push(left)
                .aggregate(builder.groupKey(builder.field(0)), builder.aggregateCall(SqlStdOperatorTable.SUM, false, null, "SUM_V", List.of(builder.field(1))))
                .push(right)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(new JoinAggregateTranspose(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running JoinAggregateTranspose test...");
        runTest();
    }
}
