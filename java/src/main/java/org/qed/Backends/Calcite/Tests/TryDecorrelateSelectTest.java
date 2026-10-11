package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.TryDecorrelateSelect;
import org.qed.RuleBuilder;

public class TryDecorrelateSelectTest {

    public static void runTest() {
        var rule = new TryDecorrelateSelect();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateSelect rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var on = base.genericPredicateOp("on_cond", true);
        var pred = base.genericPredicateOp("select_filter", true);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(l).scan(r)
                .filter(b.call(pred, b.field(0)))
                .join(JoinRelType.INNER, b.call(on, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        var plain = RuleChecks.ruleBuilder(base);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.INNER, plain.and(
                        plain.call(on, plain.field(2, 0, 0), plain.field(2, 1, 0)),
                        plain.call(pred, plain.field(2, 1, 0))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.call(on, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateSelect test...");
        runTest();
    }
}
