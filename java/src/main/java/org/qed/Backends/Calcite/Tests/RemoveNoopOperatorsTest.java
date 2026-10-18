package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.RRuleInstances.RemoveNoopOperators;
import org.qed.RuleBuilder;

import java.util.List;

public class RemoveNoopOperatorsTest {
    public static void runTest() {
        var rule = new RemoveNoopOperators();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RemoveNoopOperators rule) {
        var inputBuilder = RuleBuilder.create();
        var inputName = RuleChecks.table(inputBuilder, 1, false);
        var inputScan = RuleChecks.ruleBuilder(inputBuilder).scan(inputName).build();
        var inputRef = inputScan.getCluster().getRexBuilder().makeInputRef(inputScan, 0);
        RelNode identityProject = LogicalProject.create(inputScan, List.of(), List.of(inputRef), List.of("c0"));

        var expectedBuilder = RuleBuilder.create();
        var expectedName = RuleChecks.table(expectedBuilder, 1, false);
        RelNode expectedScan = RuleChecks.ruleBuilder(expectedBuilder).scan(expectedName).build();
        RuleChecks.rewrites(rule, identityProject, expectedScan);

        var nearBuilder = RuleBuilder.create();
        var nearName = RuleChecks.table(nearBuilder, 1, false);
        var nearScan = RuleChecks.ruleBuilder(nearBuilder).scan(nearName).build();
        var constant = nearScan.getCluster().getRexBuilder().makeExactLiteral(java.math.BigDecimal.valueOf(7));
        RelNode nonIdentityProject = LogicalProject.create(nearScan, List.of(), List.of(constant), List.of("seven"));
        RuleChecks.doesNotApply(rule, nonIdentityProject);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveNoopOperators test...");
        runTest();
    }
}
