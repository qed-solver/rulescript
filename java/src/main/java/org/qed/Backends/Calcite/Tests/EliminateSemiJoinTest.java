package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.qed.RRuleInstances.EliminateSemiJoin;
import org.qed.RuleBuilder;

public final class EliminateSemiJoinTest {
    private EliminateSemiJoinTest() {}

    public static void runTest() {
        var rule = new EliminateSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltSelfSemiJoin();
        rejectsHandBuiltDifferentRightInput();
    }

    private static void rewritesHandBuiltSelfSemiJoin() {
        var qedBuilder = RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        var scan = builder.scan(table).build();
        var before = builder.push(scan).push(scan).join(JoinRelType.SEMI, builder.literal(true)).build();
        var expected = builder.push(scan).build();
        RuleChecks.rewrites(new EliminateSemiJoin(), before, expected);
    }

    private static void rejectsHandBuiltDifferentRightInput() {
        var qedBuilder = RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        RelNode left = builder.scan(table).build();
        RelNode right = builder.scan(table).filter(RuleChecks.gt(builder, builder.field(0), 0)).build();
        var before = builder.push(left).push(right).join(JoinRelType.SEMI, builder.literal(true)).build();
        var rule = new EliminateSemiJoin();
        RuleChecks.doesNotApply(rule, before);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateSemiJoin test...");
        runTest();
    }
}
