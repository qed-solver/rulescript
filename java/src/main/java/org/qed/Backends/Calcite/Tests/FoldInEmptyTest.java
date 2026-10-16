package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.FoldInEmpty;
import org.qed.RuleBuilder;

public class FoldInEmptyTest {

    public static void runTest() {
        var rule = new FoldInEmpty();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldInEmpty rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var left = b.scan(l).build();
        var emptyRight = b.scan(r).empty().build();
        var cond = RuleChecks.eq(b, b.push(left).push(emptyRight).field(2, 0, 0), b.field(2, 1, 0));
        var before = b.join(JoinRelType.SEMI, cond).build();
        var after = b.push(left).empty().build();
        RuleChecks.rewrites(rule, before, after);

        var right = b.scan(r).build();
        RuleChecks.doesNotApply(rule, b.push(left).push(right)
                .join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build());
        RuleChecks.doesNotApply(rule, b.push(left).push(emptyRight)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FoldInEmpty test...");
        runTest();
    }
}
