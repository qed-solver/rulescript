package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.TryDecorrelateProjectSet;
import org.qed.RuleBuilder;

public class TryDecorrelateProjectSetTest {

    public static void runTest() {
        var rule = new TryDecorrelateProjectSet();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateProjectSet rule) {
        var base = RuleBuilder.create();
        var tl = RuleChecks.table(base, 1, false);
        var ti = RuleChecks.table(base, 1, false);
        var te = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(tl).scan(ti).scan(te)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .join(JoinRelType.INNER, b.and(
                        RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                        RuleChecks.eq(b, b.field(2, 1, 0), b.field(2, 1, 1))))
                .build();
        var after = b.scan(tl).scan(ti)
                .join(JoinRelType.INNER, b.literal(true))
                .scan(te)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 1), b.field(2, 1, 0)))
                .filter(b.and(
                        RuleChecks.eq(b, b.field(0), b.field(1)),
                        RuleChecks.eq(b, b.field(1), b.field(2))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var outerJoinNotInner = b.scan(tl).scan(ti).scan(te)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .join(JoinRelType.LEFT, b.and(
                        RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                        RuleChecks.eq(b, b.field(2, 1, 0), b.field(2, 1, 1))))
                .build();
        RuleChecks.doesNotApply(rule, outerJoinNotInner);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateProjectSet test...");
        runTest();
    }
}
