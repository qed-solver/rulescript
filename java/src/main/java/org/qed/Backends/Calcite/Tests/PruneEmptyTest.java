package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PruneEmpty;
import org.qed.RuleBuilder;

public class PruneEmptyTest {
    public static void runTest() {
        var rule = new PruneEmpty();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneEmpty rule) {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var builder = ruleBuilder.transform(CalciteRuntime::configure);
        var source = builder.scan(table).build();
        var condition = builder.getRexBuilder().makeLiteral(true);
        RelNode ordinaryInput = LogicalFilter.create(source, condition);
        RuleChecks.doesNotApply(rule, ordinaryInput);

        var expectedRuleBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedRuleBuilder, 1, false);
        var expectedBuilder = expectedRuleBuilder.transform(CalciteRuntime::configure);
        var expectedSource = expectedBuilder.scan(expectedTable).build();
        var matchingRuleBuilder = RuleBuilder.create();
        var matchingTable = RuleChecks.table(matchingRuleBuilder, 1, false);
        var matchingBuilder = matchingRuleBuilder.transform(CalciteRuntime::configure);
        var matchingSource = matchingBuilder.scan(matchingTable).build();
        RelNode before = LogicalFilter.create(matchingBuilder.values(matchingSource.getRowType()).build(),
                matchingBuilder.getRexBuilder().makeLiteral(true));
        RelNode after = expectedBuilder.values(expectedSource.getRowType()).build();
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneEmpty test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError(CalciteTester.failures() + " check(s) failed");
        }
    }
}
