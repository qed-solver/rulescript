package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateProject;
import org.qed.RuleBuilder;

public final class EliminateProjectTest {
    public static void runTest() {
        var rule = new EliminateProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltIdentityProject(rule);
        rejectsReorderedProject(rule);
    }

    private static void handBuiltIdentityProject(EliminateProject rule) {
        var builder = RuleBuilder.create();
        var tableName = RuleChecks.table(builder, 2, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(tableName);
        var input = b.build();
        var rex = b.getRexBuilder();
        var before = LogicalProject.create(input, java.util.List.of(), java.util.List.of(
                rex.makeInputRef(input, 0), rex.makeInputRef(input, 1)),
                input.getRowType().getFieldNames());
        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        expectedBuilder.scan(tableName);
        RuleChecks.rewrites(rule, before, expectedBuilder.build());
    }

    private static void rejectsReorderedProject(EliminateProject rule) {
        var builder = RuleBuilder.create();
        var tableName = RuleChecks.table(builder, 2, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(tableName);
        var input = b.build();
        var rex = b.getRexBuilder();
        RuleChecks.doesNotApply(rule, LogicalProject.create(input, java.util.List.of(), java.util.List.of(
                rex.makeInputRef(input, 1), rex.makeInputRef(input, 0)),
                input.getRowType().getFieldNames()));
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateProject test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError(CalciteTester.failures() + " test failure(s)");
        }
    }
}
