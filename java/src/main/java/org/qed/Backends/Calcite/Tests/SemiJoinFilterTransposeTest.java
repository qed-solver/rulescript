package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.SemiJoinFilterTranspose;
import org.qed.RuleBuilder;

public class SemiJoinFilterTransposeTest {
    public static void runTest() {
        var rule = new SemiJoinFilterTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltSemijoinMatch(rule);
        rejectsInnerJoinNearMiss();
    }

    private static void handBuiltSemijoinMatch(SemiJoinFilterTranspose rule) {
        var schema = RuleBuilder.create();
        var leftName = RuleChecks.table(schema, 2, false, false);
        var rightName = RuleChecks.table(schema, 2, false, false);
        var b = RuleChecks.ruleBuilder(schema);

        RelNode left = b.scan(leftName).build();
        RelNode right = b.scan(rightName).build();
        RelNode filteredLeft = b.push(left)
                .filter(RuleChecks.gt(b, b.field(1), 3))
                .build();
        RelNode before = b.push(filteredLeft).push(right)
                .join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();

        RelNode semi = b.push(left).push(right)
                .join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        RelNode expected = b.push(semi)
                .filter(RuleChecks.gt(b, b.field(1), 3))
                .build();
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsInnerJoinNearMiss() {
        var schema = RuleBuilder.create();
        var leftName = RuleChecks.table(schema, 2, false, false);
        var rightName = RuleChecks.table(schema, 2, false, false);
        var b = RuleChecks.ruleBuilder(schema);
        RelNode left = b.scan(leftName).build();
        RelNode right = b.scan(rightName).build();
        RelNode filteredLeft = b.push(left)
                .filter(RuleChecks.gt(b, b.field(1), 3))
                .build();
        RelNode innerJoin = b.push(filteredLeft).push(right)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(new SemiJoinFilterTranspose(), innerJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running SemiJoinFilterTranspose test...");
        runTest();
    }
}
