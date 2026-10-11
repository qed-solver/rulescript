package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.CommuteRightJoin;
import org.qed.RuleBuilder;

public class CommuteRightJoinTest {

    public static void runTest() {
        var rule = new CommuteRightJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(CommuteRightJoin rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var cond = builder.genericPredicateOp("join_cond", true);
        var plan = builder.scan(l).scan(r).join(JoinRelType.RIGHT,
                builder.call(cond, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var swapped = plain.scan(r).scan(l).join(JoinRelType.LEFT,
                plain.call(cond, plain.field(2, 1, 0), plain.field(2, 0, 0)))
                .project(plain.field(1), plain.field(0)).build();
        RuleChecks.rewrites(rule, plan, swapped);
        var inner = builder.scan(l).scan(r).join(JoinRelType.INNER,
                builder.call(cond, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(rule, inner);
    }

    public static void main(String[] args) {
        System.out.println("Running CommuteRightJoin test...");
        runTest();
    }
}
