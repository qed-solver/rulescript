package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.ConvertToLocalRelation;
import org.qed.RuleBuilder;

public final class ConvertToLocalRelationTest {
    public static void runTest() {
        var rule = new ConvertToLocalRelation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var source = builder.values(new String[]{"c0", "c1", "c2"}, 1, 2, 3, 4, 5, 6).build();
        var refs = new java.util.ArrayList<org.apache.calcite.rex.RexNode>();
        refs.add(builder.getRexBuilder().makeInputRef(source, 2));
        refs.add(builder.getRexBuilder().makeInputRef(source, 0));
        var before = org.apache.calcite.rel.logical.LogicalProject.create(source,
                java.util.List.of(), refs, java.util.List.of("c2", "c0"));
        var expectedBuilder = RuleBuilder.create();
        var after = expectedBuilder.values(new String[]{"c2", "c0"}, 3, 1, 6, 4).build();
        RuleChecks.rewrites(rule, before, after);

        var nearBuilder = RuleBuilder.create();
        var table = RuleChecks.table(nearBuilder, 3, false, false);
        var nearMiss = RuleChecks.ruleBuilder(nearBuilder).scan(table).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running ConvertToLocalRelation test...");
        runTest();
    }
}
