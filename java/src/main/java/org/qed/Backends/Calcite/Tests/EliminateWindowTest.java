package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.EliminateWindow;
import org.qed.RuleBuilder;

public class EliminateWindowTest {

    public static void runTest() {
        var rule = new EliminateWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateWindow rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 2, false, false);
        var b = RuleChecks.ruleBuilder(base);
        var input = b.scan(t).build();
        var keys = b.scan(t).aggregate(b.groupKey(0)).build();
        var before = b.push(input).push(keys)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.field(0), b.field(1))
                .build();
        RuleChecks.rewrites(rule, before, input);

        RuleChecks.doesNotApply(rule, b.push(input).push(keys)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 1), b.field(2, 1, 0)))
                .project(b.field(0), b.field(1))
                .build());
        RuleChecks.doesNotApply(rule, b.push(input).push(keys)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.field(0), b.field(1))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateWindow test...");
        runTest();
    }
}
