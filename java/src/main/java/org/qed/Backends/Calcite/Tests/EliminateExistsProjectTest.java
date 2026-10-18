package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexSubQuery;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateExistsProject;
import org.qed.RuleBuilder;

import java.util.List;

public final class EliminateExistsProjectTest {
    public static void runTest() {
        var rule = new EliminateExistsProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var outerTable = RuleChecks.table(builder, 1, false);
        var innerTable = RuleChecks.table(builder, 1, false);
        var outer = builder.scan(outerTable).build();
        var input = builder.scan(innerTable).build();
        var project = LogicalProject.create(input, List.of(), List.of(builder.literal(1)), List.of("constant"));
        var source = LogicalFilter.create(outer, RexSubQuery.exists(project));
        var expected = LogicalFilter.create(outer, RexSubQuery.exists(input));
        RuleChecks.rewrites(rule, source, expected);

        var noMatch = LogicalFilter.create(outer, RexSubQuery.exists(input));
        RuleChecks.doesNotApply(rule, noMatch);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateExistsProject test...");
        runTest();
    }
}
