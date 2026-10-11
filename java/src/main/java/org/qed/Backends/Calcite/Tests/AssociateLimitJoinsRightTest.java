package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.AssociateLimitJoinsRight;
import org.qed.RuleBuilder;

public class AssociateLimitJoinsRightTest {

    public static void runTest() {
        var rule = new AssociateLimitJoinsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AssociateLimitJoinsRight rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var tc = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(tc).scan(ta).scan(tb)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        var after = b.scan(ta).scan(tc)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .scan(tb)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.field(1), b.field(0), b.field(2))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var outerReadsB = b.scan(tc).scan(ta).scan(tb)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 1)))
                .build();
        RuleChecks.doesNotApply(rule, outerReadsB);
    }

    public static void main(String[] args) {
        System.out.println("Running AssociateLimitJoinsRight test...");
        runTest();
    }
}
