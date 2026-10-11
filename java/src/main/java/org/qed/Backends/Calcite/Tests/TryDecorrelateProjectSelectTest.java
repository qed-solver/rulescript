package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.TryDecorrelateProjectSelect;
import org.qed.RuleBuilder;

public class TryDecorrelateProjectSelectTest {

    public static void runTest() {
        var rule = new TryDecorrelateProjectSelect();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateProjectSelect rule) {
        var base = RuleBuilder.create();
        var left = RuleChecks.table(base, 1, false, false);
        var inner = RuleChecks.table(base, 2, false, false);
        var b = RuleChecks.ruleBuilder(base);
        var keepV = b.scan(inner)
                .filter(RuleChecks.gt(b, b.field(0), 5))
                .project(b.field(1))
                .build();
        var before = b.scan(left).push(keepV)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        var after = b.scan(left).scan(inner)
                .join(JoinRelType.LEFT, b.and(
                        RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 1)),
                        RuleChecks.gt(b, b.field(2, 1, 0), 5)))
                .project(b.field(0), b.field(2))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var innerJoin = b.scan(left).push(keepV)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, innerJoin);

        var keepK = b.scan(inner)
                .filter(RuleChecks.gt(b, b.field(0), 5))
                .project(b.field(0))
                .build();
        var keepKJoin = b.scan(left).push(keepK)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, keepKJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateProjectSelect test...");
        runTest();
    }
}
