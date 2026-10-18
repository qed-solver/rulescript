package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.EliminateSetRight;
import org.qed.RuleBuilder;

public final class EliminateSetRightTest {
    public static void runTest() {
        var rule = new EliminateSetRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltUnionWithEmptyLeft(rule);
        preservesUnionWithNonemptyLeft(rule);
    }

    private static void handBuiltUnionWithEmptyLeft(EliminateSetRight rule) {
        var builder = RuleBuilder.create();
        var leftTable = RuleChecks.table(builder, 3, false);
        var rightTable = RuleChecks.table(builder, 3, false);
        var leftTemplate = builder.scan(leftTable).project(builder.field(2), builder.field(0), builder.field(1)).build();
        var left = org.apache.calcite.rel.logical.LogicalValues.createEmpty(builder.getCluster(), leftTemplate.getRowType());
        var right = builder.scan(rightTable).project(builder.field(2), builder.field(0), builder.field(1)).build();
        RelNode input = builder.push(right).push(left).union(true, 2).build();
        RuleChecks.rewrites(rule, input, right);
    }

    private static void preservesUnionWithNonemptyLeft(EliminateSetRight rule) {
        var builder = RuleBuilder.create();
        var leftTable = RuleChecks.table(builder, 3, false);
        var rightTable = RuleChecks.table(builder, 3, false);
        var left = builder.scan(leftTable).project(builder.field(2), builder.field(0), builder.field(1)).build();
        var right = builder.scan(rightTable).project(builder.field(2), builder.field(0), builder.field(1)).build();
        RelNode input = builder.push(left).push(right).union(true, 2).build();
        RuleChecks.doesNotApply(rule, input);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateSetRight test...");
        runTest();
    }
}
