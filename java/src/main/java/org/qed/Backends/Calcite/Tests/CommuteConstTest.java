package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.CommuteConst;
import org.qed.RuleBuilder;

public final class CommuteConstTest {
    public static void runTest() {
        var rule = new CommuteConst();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var qedBuilder = RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        RelNode before = builder.scan(table).scan(table)
                .join(JoinRelType.INNER, builder.literal(true))
                .filter(RuleChecks.eq(builder, builder.field(0), builder.field(1)))
                .build();

        var expectedQedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedQedBuilder, 1, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedQedBuilder);
        RelNode expected = expectedBuilder.scan(expectedTable).scan(expectedTable)
                .join(JoinRelType.INNER, expectedBuilder.literal(true))
                .filter(RuleChecks.eq(expectedBuilder, expectedBuilder.field(1), expectedBuilder.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, expected);

        var leftQedBuilder = RuleBuilder.create();
        var leftTable = RuleChecks.table(leftQedBuilder, 1, false);
        var leftBuilder = RuleChecks.ruleBuilder(leftQedBuilder);
        RelNode leftJoin = leftBuilder.scan(leftTable).scan(leftTable)
                .join(JoinRelType.LEFT, leftBuilder.literal(true))
                .filter(RuleChecks.eq(leftBuilder, leftBuilder.field(0), leftBuilder.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, leftJoin);
    }

    public static void main(String[] args) throws java.io.IOException {
        System.out.println("Running CommuteConst test...");
        CalciteTester.main(new String[]{"CommuteConst"});
    }
}
