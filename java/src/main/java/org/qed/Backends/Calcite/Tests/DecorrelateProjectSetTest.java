package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.DecorrelateProjectSet;
import org.qed.RuleBuilder;

public class DecorrelateProjectSetTest {

    public static void runTest() {
        var rule = new DecorrelateProjectSet();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(DecorrelateProjectSet rule) {
        var base = RuleBuilder.create();
        var left = RuleChecks.table(base, 1, false);
        var emitted = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(left).scan(emitted)
                .join(JoinRelType.INNER, RuleChecks.gt(b, b.field(2, 1, 0), 0))
                .build();
        b.scan(left).scan(emitted).filter(RuleChecks.gt(b, b.field(0), 0));
        var after = b.join(JoinRelType.INNER, b.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(left).scan(emitted)
                .join(JoinRelType.INNER, RuleChecks.gt(b, b.field(2, 0, 0), 0))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(left).scan(emitted)
                .join(JoinRelType.LEFT, RuleChecks.gt(b, b.field(2, 1, 0), 0))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running DecorrelateProjectSet test...");
        runTest();
    }
}
