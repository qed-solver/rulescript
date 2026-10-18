package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateGroupByProject;
import org.qed.RuleBuilder;

public final class EliminateGroupByProjectTest {
    public static void runTest() {
        var rule = new EliminateGroupByProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsHandBuiltNearMiss();
    }

    private static void rewritesHandBuiltPlan() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var before = builder.scan(table).project(builder.field(0)).aggregate(builder.groupKey(0)).build();
        var expected = builder.scan(table).aggregate(builder.groupKey(0)).build();
        RuleChecks.rewrites(new EliminateGroupByProject(), before, expected);
    }

    private static void rejectsHandBuiltNearMiss() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        RelNode plan = builder.scan(table).project(builder.field(1)).aggregate(builder.groupKey(0)).build();
        RuleChecks.doesNotApply(new EliminateGroupByProject(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateGroupByProject test...");
        runTest();
    }
}
