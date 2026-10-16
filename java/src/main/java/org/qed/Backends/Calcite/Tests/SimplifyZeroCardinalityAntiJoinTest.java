package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.SimplifyZeroCardinalityAntiJoin;
import org.qed.RuleBuilder;

public class SimplifyZeroCardinalityAntiJoinTest {

    public static void runTest() {
        var rule = new SimplifyZeroCardinalityAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SimplifyZeroCardinalityAntiJoin rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).values(new String[]{"col0"}, 0).join(JoinRelType.ANTI, b.literal(true)).build();
        var after = b.scan(t).empty().build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).values(new String[]{"col0"}, 0, 1)
                .join(JoinRelType.ANTI, b.literal(true)).build());
        RuleChecks.doesNotApply(rule, b.scan(t).values(new String[]{"col0"}, 0)
                .join(JoinRelType.INNER, b.literal(true)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyZeroCardinalityAntiJoin test...");
        runTest();
    }
}
