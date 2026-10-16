package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.qed.RRuleInstances.UnionToValues;
import org.qed.RuleBuilder;

import java.util.List;

public class UnionToValuesTest {

    public static void runTest() {
        var rule = new UnionToValues();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(UnionToValues rule) {
        var base = RuleBuilder.create();
        var b = RuleChecks.ruleBuilder(base);
        var names = new String[]{"c0", "c1"};
        var union = (java.util.function.BiFunction<Boolean, Integer, RelNode>) (all, last) -> LogicalUnion.create(List.of(
                b.values(names, 1, 2, 3, 4).build(),
                b.values(names, 3, 4, 5, 6).build(),
                b.values(names, 7, last).build()), all);
        var before = union.apply(true, 8);
        var after = b.values(names, 1, 2, 3, 4, 3, 4, 5, 6, 7, 8).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, union.apply(true, 9));
        RuleChecks.doesNotApply(rule, union.apply(false, 8));
    }

    public static void main(String[] args) {
        System.out.println("Running UnionToValues test...");
        runTest();
    }
}
