package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.LeftAssociateJoinsRight;
import org.qed.RuleBuilder;

public class LeftAssociateJoinsRightTest {

    public static void runTest() {
        var rule = new LeftAssociateJoinsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(LeftAssociateJoinsRight rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var tc = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(ta).scan(tb).join(JoinRelType.INNER, b.literal(true))
                .scan(tc)
                .join(JoinRelType.INNER, b.and(
                        RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                        RuleChecks.eq(b, b.field(2, 0, 1), b.field(2, 1, 0))))
                .build();
        var after = b.scan(ta).scan(tb).scan(tc)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var innerNotTrue = b.scan(ta).scan(tb)
                .join(JoinRelType.LEFT, b.literal(true))
                .scan(tc)
                .join(JoinRelType.INNER, b.and(
                        RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                        RuleChecks.eq(b, b.field(2, 0, 1), b.field(2, 1, 0))))
                .build();
        RuleChecks.doesNotApply(rule, innerNotTrue);
    }

    public static void main(String[] args) {
        System.out.println("Running LeftAssociateJoinsRight test...");
        runTest();
    }
}
