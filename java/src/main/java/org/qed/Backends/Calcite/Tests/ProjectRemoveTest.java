package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.ProjectRemove;

public class ProjectRemoveTest {
    public static void runTest() {
        var rule = new ProjectRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectRemove rule) {
        var builder = RuleBuilder.create();
        var one = RuleChecks.table(builder, 1, false);
        var scanBuilder = RuleChecks.ruleBuilder(builder);
        var scan = scanBuilder.scan(one).build();
        var planBuilder = RuleChecks.ruleBuilder(builder);
        var input = (RelNode) planBuilder.scan(one).build();
        var project = LogicalProject.create(input, java.util.List.of(),
                java.util.List.of(planBuilder.getRexBuilder().makeInputRef(input, 0)),
                java.util.List.of(input.getRowType().getFieldList().get(0).getName()));
        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.push(scan).build();
        RuleChecks.rewrites(rule, project, expected);

        var wideBuilder = RuleChecks.ruleBuilder(builder);
        var wide = RuleChecks.table(builder, 2, false);
        var wideInput = wideBuilder.scan(wide).build();
        var wideProject = LogicalProject.create(wideInput, java.util.List.of(),
                java.util.List.of(wideBuilder.getRexBuilder().makeInputRef(wideInput, 0)),
                java.util.List.of(wideInput.getRowType().getFieldList().get(0).getName()));
        RuleChecks.doesNotApply(rule, wideProject);
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectRemove test...");
        runTest();
    }
}
