package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.ConvertGroupByToDistinct;
import org.qed.RuleBuilder;

import java.util.List;

public class ConvertGroupByToDistinctTest {
    public static void runTest() {
        var rule = new ConvertGroupByToDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsAggregateWithCall();
    }

    private static void rewritesHandBuiltPlan() {
        var sourceSchema = RuleBuilder.create();
        var table = RuleChecks.table(sourceSchema, 2, false, false);
        var sourceBuilder = RuleChecks.ruleBuilder(sourceSchema);
        var input = sourceBuilder.scan(table).build();
        var before = LogicalAggregate.create(input, List.of(), ImmutableBitSet.of(0, 1), null, List.of());

        var expectedSchema = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedSchema, 2, false, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedSchema);
        RelNode firstInput = expectedBuilder.scan(expectedTable).build();
        RelNode secondInput = expectedBuilder.scan(expectedTable).build();
        var after = LogicalIntersect.create(List.of(firstInput, secondInput), false);

        RuleChecks.rewrites(new ConvertGroupByToDistinct(), before, after);
    }

    private static void rejectsAggregateWithCall() {
        var schema = RuleBuilder.create();
        var table = RuleChecks.table(schema, 2, false, false);
        var builder = RuleChecks.ruleBuilder(schema);
        var plan = builder.scan(table)
                .aggregate(builder.groupKey(0, 1), builder.count(false, "row_count"))
                .build();
        RuleChecks.doesNotApply(new ConvertGroupByToDistinct(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running ConvertGroupByToDistinct test...");
        runTest();
    }
}
