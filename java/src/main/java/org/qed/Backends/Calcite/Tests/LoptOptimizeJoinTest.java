package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.LoptOptimizeJoin;
import org.qed.RuleBuilder;

public class LoptOptimizeJoinTest {

    public static void runTest() {
        var rule = new LoptOptimizeJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(LoptOptimizeJoin rule) {
        var base = RuleBuilder.create();
        var a = RuleChecks.table(base, 1, false);
        var bt = RuleChecks.table(base, 1, false);
        var c = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var ab = b.scan(a).scan(bt).join(JoinRelType.INNER, b.literal(true)).build();
        var chain = (java.util.function.Function<JoinRelType, org.apache.calcite.rel.RelNode>) type -> b.push(ab).scan(c)
                .join(type, b.and(RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 0, 1)),
                        RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))))
                .build();
        var before = chain.apply(JoinRelType.INNER);
        var ac = b.scan(a).scan(c).join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build();
        var after = b.push(ac).scan(bt)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.field(0), b.field(2), b.field(1))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, chain.apply(JoinRelType.LEFT));
        RuleChecks.doesNotApply(rule, b.push(ab).scan(c)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 0, 1)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running LoptOptimizeJoin test...");
        runTest();
    }
}
