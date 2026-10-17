package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PruneWindowOutputCols;
import org.qed.RuleBuilder;

import java.util.List;

public final class PruneWindowOutputColsTest {
    public static void runTest() {
        var rule = new PruneWindowOutputCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneWindowOutputCols rule) {
        var rules = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(rules);
        var inputName = RuleChecks.table(rules, 2, true, false);
        var before = windowPlan(builder, inputName, rules.genericAggregateOp("w1", org.qed.RelType.fromString("INTEGER", true)),
                rules.genericAggregateOp("w2", org.qed.RelType.fromString("INTEGER", true)));

        var expectedRules = RuleBuilder.create();
        var expectedBuilder = RuleChecks.ruleBuilder(expectedRules);
        var expectedName = RuleChecks.table(expectedRules, 2, true, false);
        var after = windowPlan(expectedBuilder, expectedName,
                expectedRules.genericAggregateOp("w1", org.qed.RelType.fromString("INTEGER", true)), null);
        RuleChecks.rewrites(rule, before, after);

        var missRules = RuleBuilder.create();
        var missBuilder = RuleChecks.ruleBuilder(missRules);
        var missName = RuleChecks.table(missRules, 2, true, false);
        RelNode noWindow = missBuilder.scan(missName).build();
        RuleChecks.doesNotApply(rule, noWindow);
    }

    private static RelNode windowPlan(RelBuilder builder, String inputName, SqlAggFunction first, SqlAggFunction second) {
        var input = builder.scan(inputName).build();
        builder.push(input);
        var calls = new java.util.ArrayList<RelBuilder.AggCall>();
        calls.add(builder.aggregateCall(first, false, null, "w1", List.of(builder.field(1))));
        if (second != null) {
            calls.add(builder.aggregateCall(second, false, null, "w2", List.of(builder.field(1))));
        }
        builder.aggregate(builder.groupKey(builder.field(0)), calls);
        var perPart = builder.build();
        builder.push(input);
        builder.push(perPart);
        builder.join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS,
                builder.field(2, 0, 0), builder.field(2, 1, 0)));
        builder.project(builder.field(0), builder.field(1), builder.field(3));
        return builder.build();
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
