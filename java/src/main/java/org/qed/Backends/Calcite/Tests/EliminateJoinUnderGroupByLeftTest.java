package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.EliminateJoinUnderGroupByLeft;
import org.qed.RuleBuilder;

import java.util.function.Supplier;

public class EliminateJoinUnderGroupByLeftTest {

    public static void runTest() {
        var rule = new EliminateJoinUnderGroupByLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateJoinUnderGroupByLeft rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        Supplier<RelBuilder> leftJoin = () -> b.scan(l).scan(r)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = leftJoin.get().aggregate(b.groupKey(0)).build();
        var after = b.scan(l).aggregate(b.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, leftJoin.get().aggregate(b.groupKey(1)).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .aggregate(b.groupKey(0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinUnderGroupByLeft test...");
        runTest();
    }
}
