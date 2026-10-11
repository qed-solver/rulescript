package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.TryDecorrelateProject;
import org.qed.RuleBuilder;

public class TryDecorrelateProjectTest {

    public static void runTest() {
        var rule = new TryDecorrelateProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateProject rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 2, false);
        var cond = builder.genericPredicateOp("on", true);
        var before = builder.scan(l).scan(r)
                .project(builder.field(1), builder.field(0))
                .join(JoinRelType.INNER, builder.call(cond,
                        builder.field(2, 0, 0), builder.field(2, 1, 0), builder.field(2, 1, 1)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.INNER, plain.literal(true))
                .project(plain.field(0), plain.field(2), plain.field(1))
                .filter(plain.call(cond, plain.field(0), plain.field(1), plain.field(2)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var left = builder.scan(l).scan(r)
                .project(builder.field(1), builder.field(0))
                .join(JoinRelType.LEFT, builder.call(cond,
                        builder.field(2, 0, 0), builder.field(2, 1, 0), builder.field(2, 1, 1)))
                .build();
        RuleChecks.doesNotApply(rule, left);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateProject test...");
        runTest();
    }
}
