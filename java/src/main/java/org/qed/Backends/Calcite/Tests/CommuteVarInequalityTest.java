package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.CommuteVarInequality;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public class CommuteVarInequalityTest {
    public static void runTest() {
        var rule = new CommuteVarInequality();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);
        var builder = RuleBuilder.create();
        var left = table(builder, 1, false);
        var right = table(builder, 1, false);
        builder.scan(left).scan(right);
        var beforeBuilder = builder;
        var beforeJoin = beforeBuilder.join(JoinRelType.INNER, beforeBuilder.literal(true)).build();
        var before = beforeBuilder.push(beforeJoin).filter(beforeBuilder.call(SqlStdOperatorTable.LESS_THAN_OR_EQUAL,
                beforeBuilder.field(0), beforeBuilder.field(1))).build();
        var expectedBuilder = RuleBuilder.create();
        var expectedLeft = table(expectedBuilder, 1, false);
        var expectedRight = table(expectedBuilder, 1, false);
        expectedBuilder.scan(expectedLeft).scan(expectedRight);
        var expectedJoinBuilder = expectedBuilder;
        var expectedJoin = expectedJoinBuilder.join(JoinRelType.INNER, expectedJoinBuilder.literal(true)).build();
        var expected = expectedJoinBuilder.push(expectedJoin).filter(expectedJoinBuilder.call(
                SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, expectedJoinBuilder.field(1),
                expectedJoinBuilder.field(0))).build();
        rewrites(rule, before, expected);
        var nearBuilder = RuleBuilder.create();
        var nearLeft = table(nearBuilder, 1, false);
        var nearRight = table(nearBuilder, 1, false);
        nearBuilder.scan(nearLeft).scan(nearRight);
        var nearJoinBuilder = nearBuilder;
        var nearJoin = nearJoinBuilder.join(JoinRelType.LEFT, nearJoinBuilder.literal(true)).build();
        var nearMiss = nearJoinBuilder.push(nearJoin).filter(nearJoinBuilder.call(SqlStdOperatorTable.LESS_THAN_OR_EQUAL,
                nearJoinBuilder.field(0), nearJoinBuilder.field(1))).build();
        doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running CommuteVarInequality test...");
        runTest();
    }
}
