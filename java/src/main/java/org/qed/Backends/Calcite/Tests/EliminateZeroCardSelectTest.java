package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.qed.RRuleInstances.EliminateZeroCardSelect;
import org.qed.RuleBuilder;

public class EliminateZeroCardSelectTest {

    public static void runTest() {
        var rule = new EliminateZeroCardSelect();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateZeroCardSelect rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var input = b.scan(t).empty().build();
        var before = LogicalFilter.create(
                LogicalFilter.create(input, RuleChecks.gt(b, b.push(input).field(0), 5)),
                RuleChecks.gt(b, b.push(input).field(0), 10));
        var after = b.scan(t).empty().build();
        RuleChecks.rewrites(rule, before, after);

        var single = LogicalFilter.create(input, RuleChecks.gt(b, b.push(input).field(0), 5));
        RuleChecks.doesNotApply(rule, single);

        var nonEmpty = LogicalFilter.create(
                LogicalFilter.create(b.scan(t).build(), RuleChecks.gt(b, b.field(0), 5)),
                RuleChecks.gt(b, b.field(0), 10));
        RuleChecks.doesNotApply(rule, nonEmpty);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateZeroCardSelect test...");
        runTest();
    }
}
