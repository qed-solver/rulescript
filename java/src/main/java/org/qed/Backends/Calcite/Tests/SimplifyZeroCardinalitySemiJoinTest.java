package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.SimplifyZeroCardinalitySemiJoin;
import org.qed.RuleBuilder;

public class SimplifyZeroCardinalitySemiJoinTest {
    public static void runTest() {
        var rule = new SimplifyZeroCardinalitySemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var schema = RuleBuilder.create();
        var l = RuleChecks.table(schema, 1, false, false);
        var r = RuleChecks.table(schema, 1, false, false);
        var plans = RuleChecks.ruleBuilder(schema);
        var expectedEmpty = plans.scan(l).filter(plans.literal(false)).build();
        var handBuilt = plans.scan(l).scan(r).filter(plans.literal(false))
                .join(JoinRelType.SEMI, plans.literal(true)).build();
        RuleChecks.rewrites(rule, handBuilt, expectedEmpty);
        RuleChecks.doesNotApply(rule, plans.scan(l).scan(r).join(JoinRelType.INNER, plans.literal(true)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyZeroCardinalitySemiJoin test...");
        runTest();
    }
}
