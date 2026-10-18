package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.JoinAddRedundantSemiJoin;
import org.qed.RuleBuilder;

public class JoinAddRedundantSemiJoinTest {
    public static void runTest() {
        var rule = new JoinAddRedundantSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltJoin(rule);
        handBuiltNoMatch(rule);
    }

    private static void handBuiltJoin(JoinAddRedundantSemiJoin rule) {
        var beforeBuilder = RuleBuilder.create();
        var left = RuleChecks.table(beforeBuilder, 1, false);
        var right = RuleChecks.table(beforeBuilder, 1, false);
        beforeBuilder.scan(left).scan(right);
        var condition = beforeBuilder.call(beforeBuilder.genericPredicateOp("matches", true),
                beforeBuilder.joinFields().asJava());
        RelNode before = beforeBuilder.join(JoinRelType.INNER, condition).build();

        var expectedBuilder = RuleBuilder.create();
        left = RuleChecks.table(expectedBuilder, 1, false);
        right = RuleChecks.table(expectedBuilder, 1, false);
        expectedBuilder.scan(left);
        var leftScan = expectedBuilder.build();
        expectedBuilder.scan(right);
        var rightScan = expectedBuilder.build();
        expectedBuilder.push(leftScan).push(rightScan);
        condition = expectedBuilder.call(expectedBuilder.genericPredicateOp("matches", true),
                expectedBuilder.joinFields().asJava());
        expectedBuilder.join(JoinRelType.SEMI, condition)
                .push(rightScan).join(JoinRelType.INNER, condition);
        RelNode expected = expectedBuilder.build();
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void handBuiltNoMatch(JoinAddRedundantSemiJoin rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        builder.scan(left).scan(right);
        var condition = builder.call(builder.genericPredicateOp("matches", true), builder.joinFields().asJava());
        RelNode plan = builder.join(JoinRelType.LEFT, condition).build();
        RuleChecks.doesNotApply(rule, plan);
    }

    public static void main(String[] args) {
        System.out.println("Running JoinAddRedundantSemiJoin test...");
        runTest();
    }
}
