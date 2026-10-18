package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ProjectCalcMerge;
import org.qed.RuleBuilder;

public class ProjectCalcMergeTest {
    public static void runTest() {
        System.out.println("Running RULE test...");
        var rule = new ProjectCalcMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectCalcMerge rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var b = RuleChecks.ruleBuilder(builder);
        var input = b.scan(table).build();
        var x = b.getRexBuilder().makeInputRef(input, 0);
        var y = b.getRexBuilder().makeInputRef(input, 1);
        var lower0 = b.call(SqlStdOperatorTable.PLUS, x, b.literal(3));
        var lower1 = b.call(SqlStdOperatorTable.MULTIPLY, y, b.literal(2));
        var condition = RuleChecks.gt(b, x, 0);
        var before = org.apache.calcite.rel.logical.LogicalProject.create(
                org.apache.calcite.rel.logical.LogicalProject.create(
                        org.apache.calcite.rel.logical.LogicalFilter.create(input, condition),
                        java.util.List.of(), java.util.List.of(lower0, lower1),
                        java.util.List.of("L0", "L1")),
                java.util.List.of(), java.util.List.of(
                        b.call(SqlStdOperatorTable.PLUS, b.getRexBuilder().makeInputRef(
                                org.apache.calcite.rel.logical.LogicalProject.create(
                                        org.apache.calcite.rel.logical.LogicalFilter.create(input, condition),
                                        java.util.List.of(), java.util.List.of(lower0, lower1),
                                        java.util.List.of("L0", "L1")), 0), b.literal(4)),
                        b.call(SqlStdOperatorTable.MULTIPLY, b.getRexBuilder().makeInputRef(
                                org.apache.calcite.rel.logical.LogicalProject.create(
                                        org.apache.calcite.rel.logical.LogicalFilter.create(input, condition),
                                        java.util.List.of(), java.util.List.of(lower0, lower1),
                                        java.util.List.of("L0", "L1")), 1), b.literal(5))),
                java.util.List.of("P0", "P1"));
        var expected = org.apache.calcite.rel.logical.LogicalProject.create(
                org.apache.calcite.rel.logical.LogicalFilter.create(input, condition),
                java.util.List.of(), java.util.List.of(
                        b.call(SqlStdOperatorTable.PLUS, lower0, b.literal(4)),
                        b.call(SqlStdOperatorTable.MULTIPLY, lower1, b.literal(5))),
                java.util.List.of("P0", "P1"));
        RuleChecks.rewrites(rule, before, expected);

        var wrongArity = org.apache.calcite.rel.logical.LogicalProject.create(
                org.apache.calcite.rel.logical.LogicalProject.create(
                        org.apache.calcite.rel.logical.LogicalFilter.create(input, condition),
                        java.util.List.of(), java.util.List.of(lower0),
                        java.util.List.of("L0")),
                java.util.List.of(), java.util.List.of(b.getRexBuilder().makeInputRef(
                        org.apache.calcite.rel.logical.LogicalProject.create(
                                org.apache.calcite.rel.logical.LogicalFilter.create(input, condition),
                                java.util.List.of(), java.util.List.of(lower0), java.util.List.of("L0")), 0),
                        b.getRexBuilder().makeInputRef(
                                org.apache.calcite.rel.logical.LogicalProject.create(
                                        org.apache.calcite.rel.logical.LogicalFilter.create(input, condition),
                                        java.util.List.of(), java.util.List.of(lower0), java.util.List.of("L0")), 0)),
                java.util.List.of("P0", "P1"));
        RuleChecks.doesNotApply(rule, wrongArity);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        var rule = new ProjectCalcMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
