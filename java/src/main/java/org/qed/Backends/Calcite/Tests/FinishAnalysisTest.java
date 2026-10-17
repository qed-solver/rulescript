package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlOperator;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FinishAnalysis;
import org.qed.RuleBuilder;
import org.qed.RelType;

import java.util.List;

public final class FinishAnalysisTest {
    public static void runTest() {
        var rule = new FinishAnalysis();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var before = plan("g", false, false);
        var expected = plan("g", true, false);
        RuleChecks.rewrites(rule, before, expected);
        RuleChecks.doesNotApply(rule, plan("h", false, true));
    }

    private static RelNode plan(String groupingFunction, boolean extracted, boolean dependsOnAggregateColumn) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        relBuilder.scan(table);
        var input = relBuilder.build();
        relBuilder.push(input);
        SqlOperator groupOperator = builder.genericProjectionOp(groupingFunction,
                RelType.fromString("INTEGER", true));
        var key = dependsOnAggregateColumn
                ? relBuilder.call(groupOperator, relBuilder.field(0), relBuilder.field(1))
                : relBuilder.call(groupOperator, relBuilder.field(0));
        if (extracted) {
            relBuilder.project(List.of(relBuilder.field(1), key), List.of("b", "g"), true);
        } else {
            relBuilder.project(List.of(key, relBuilder.field(1)), List.of("g", "b"), true);
        }
        SqlAggFunction aggregate = builder.genericAggregateOp("f", RelType.fromString("INTEGER", true));
        var groupIndex = extracted ? 1 : 0;
        var argumentIndex = extracted ? 0 : 1;
        relBuilder.aggregate(relBuilder.groupKey(relBuilder.field(groupIndex)),
                relBuilder.aggregateCall(aggregate, false, null, "f", List.of(relBuilder.field(argumentIndex))));
        return relBuilder.build();
    }

    public static void main(String[] args) {
        System.out.println("Running FinishAnalysis test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
