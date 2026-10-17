package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRule;
import org.qed.RRuleInstances.JoinUnionTranspose;
import org.qed.RuleBuilder;

public class JoinUnionTransposeTest {
    public static void runTest() {
        var rule = new JoinUnionTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }

    private static void ordinaryTables(RRule rule) {
        var builder = RuleBuilder.create();
        var x = RuleChecks.table(builder, 1, false);
        var y = RuleChecks.table(builder, 1, false);
        var o = RuleChecks.table(builder, 1, false);
        SqlOperator predicate = builder.genericPredicateOp("ordinaryJoinPredicate", true);
        var left = builder.scan(x).scan(y).union(true, 2).build();
        var before = builder.push(left).scan(o)
                .join(JoinRelType.INNER, builder.call(predicate,
                        builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();

        var expectedBuilder = RuleBuilder.create();
        var expectedX = RuleChecks.table(expectedBuilder, 1, false);
        var expectedY = RuleChecks.table(expectedBuilder, 1, false);
        var expectedO = RuleChecks.table(expectedBuilder, 1, false);
        SqlOperator expectedPredicate = expectedBuilder.genericPredicateOp("ordinaryJoinPredicate", true);
        var joinedX = expectedBuilder.scan(expectedX).scan(expectedO)
                .join(JoinRelType.INNER, expectedBuilder.call(expectedPredicate,
                        expectedBuilder.field(2, 0, 0), expectedBuilder.field(2, 1, 0))).build();
        var joinedY = expectedBuilder.scan(expectedY).scan(expectedO)
                .join(JoinRelType.INNER, expectedBuilder.call(expectedPredicate,
                        expectedBuilder.field(2, 0, 0), expectedBuilder.field(2, 1, 0))).build();
        var after = expectedBuilder.push(joinedX).push(joinedY).union(true, 2).build();
        RuleChecks.rewrites(rule, before, after);
    }
}
