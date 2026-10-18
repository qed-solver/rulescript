package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PruneWindowInputCols;
import org.qed.RuleBuilder;

import java.util.List;

public final class PruneWindowInputColsTest {
    public static void runTest() {
        var rule = new PruneWindowInputCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneWindowInputCols rule) {
        var ruleBuilder = RuleBuilder.create();
        var builder = ruleBuilder.transform(CalciteRuntime::configure);
        var inputName = RuleChecks.table(ruleBuilder, 3, false);
        var top = ruleBuilder.genericProjectionOp("Top", org.qed.RelType.fromString("INTEGER", true));
        var window = windowPlan(builder, inputName, top, false);
        RuleChecks.doesNotApply(rule, window);
        var nearMiss = windowPlan(builder, inputName, top, true);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static org.apache.calcite.rel.RelNode windowPlan(RelBuilder builder, String inputName,
            SqlOperator top, boolean wrongPartition) {
        var input = builder.scan(inputName).build();
        builder.push(input);
        builder.project(List.of(builder.field(0), builder.field(1)), List.of("k", "v"));
        var pruned = builder.build();
        builder.push(pruned);
        builder.aggregate(builder.groupKey(builder.field(0)), List.of(builder.aggregateCall(
                RuleBuilder.create().genericAggregateOp("w", org.qed.RelType.fromString("INTEGER", true)),
                false, null, "w", List.of(builder.field(1)))));
        var perPart = builder.build();
        builder.push(input);
        builder.push(perPart);
        var leftKey = builder.field(2, 0, 0);
        var rightKey = builder.field(2, 1, wrongPartition ? 1 : 0);
        builder.join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, leftKey, rightKey));
        var full = builder.build();
        builder.push(full);
        builder.project(List.of(builder.call(top, builder.field(0), builder.field(1), builder.field(4))), List.of("out"));
        return builder.build();
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
