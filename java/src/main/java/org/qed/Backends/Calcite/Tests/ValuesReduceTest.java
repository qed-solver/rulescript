package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.qed.RRuleInstances.ValuesReduce;
import org.qed.RuleBuilder;

import java.util.List;

public class ValuesReduceTest {
    public static void runTest() {
        var rule = new ValuesReduce();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleChecks.ruleBuilder(RuleBuilder.create());
        var changedRows = RuleChecks.ruleBuilder(RuleBuilder.create())
                .values(new String[]{"a", "b", "c"}, 1, 2, 99, 4, 5, 6).build();
        RuleChecks.doesNotApply(rule, LogicalProject.create(changedRows, List.of(), List.<RexNode>of(
                builder.getRexBuilder().makeInputRef(changedRows, 2),
                builder.getRexBuilder().makeInputRef(changedRows, 0)), List.of("c", "a")));

        var directBuilder = RuleChecks.ruleBuilder(RuleBuilder.create());
        var directValues = directBuilder.values(new String[]{"a", "b", "c"}, 1, 2, 3, 4, 5, 6).build();
        RuleChecks.rewrites(rule, LogicalProject.create(directValues, List.of(), List.<RexNode>of(
                directBuilder.getRexBuilder().makeInputRef(directValues, 2),
                directBuilder.getRexBuilder().makeInputRef(directValues, 0)), List.of("c", "a")),
                directBuilder.values(new String[]{"c", "a"}, 3, 1, 6, 4).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ValuesReduce test...");
        runTest();
    }
}
