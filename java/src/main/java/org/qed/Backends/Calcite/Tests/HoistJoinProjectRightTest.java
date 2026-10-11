package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.HoistJoinProjectRight;
import org.qed.RuleBuilder;

public class HoistJoinProjectRightTest {

    public static void runTest() {
        var rule = new HoistJoinProjectRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(HoistJoinProjectRight rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 2, false);
        var cond = builder.genericPredicateOp("C", true);
        var before = builder.scan(l).scan(r)
                .project(builder.field(1), builder.field(0))
                .join(JoinRelType.LEFT, builder.call(cond,
                        builder.field(2, 0, 0), builder.field(2, 1, 0), builder.field(2, 1, 1)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.LEFT, plain.call(cond,
                        plain.field(2, 0, 0), plain.field(2, 1, 1), plain.field(2, 1, 0)))
                .project(plain.field(0), plain.field(2), plain.field(1))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var inner = builder.scan(l).scan(r)
                .project(builder.field(1), builder.field(0))
                .join(JoinRelType.INNER, builder.call(cond,
                        builder.field(2, 0, 0), builder.field(2, 1, 0), builder.field(2, 1, 1)))
                .build();
        RuleChecks.doesNotApply(rule, inner);
    }

    public static void main(String[] args) {
        System.out.println("Running HoistJoinProjectRight test...");
        runTest();
    }
}
