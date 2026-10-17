package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import java.util.List;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.InferFiltersFromConstraints;
import org.qed.RuleBuilder;

public final class InferFiltersFromConstraintsTest {
    public static void runTest() {
        var rule = new InferFiltersFromConstraints();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltJoin();
        rejectsHandBuiltInequalityJoin();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("InferFiltersFromConstraints test failed");
        }
    }

    private static void rewritesHandBuiltJoin() {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var plan = builder.scan(l).scan(r).join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.AND,
                        RuleChecks.eq(builder, builder.joinFields().get(0), builder.joinFields().get(1)),
                        RuleChecks.gt(builder, builder.joinFields().get(0), 5)));
        var before = builder.build();

        var expectedBuilder = RuleBuilder.create();
        var expectedL = RuleChecks.table(expectedBuilder, 1, false);
        var expectedR = RuleChecks.table(expectedBuilder, 1, false);
        var leftScan = expectedBuilder.scan(expectedL).build();
        var rightScan = expectedBuilder.scan(expectedR).build();
        var leftRef = expectedBuilder.getRexBuilder().makeInputRef(leftScan, 0);
        var rightRef = expectedBuilder.getRexBuilder().makeInputRef(rightScan, 0);
        var expectedLeftCondition = expectedBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.AND,
                RuleChecks.gt(expectedBuilder, leftRef, 5),
                expectedBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL, leftRef));
        var expectedLeft = org.apache.calcite.rel.logical.LogicalFilter.create(leftScan, expectedLeftCondition);
        var expectedRight = org.apache.calcite.rel.logical.LogicalFilter.create(rightScan,
                expectedBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL, rightRef));
        var joinCondition = expectedBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.AND,
                RuleChecks.eq(expectedBuilder,
                        expectedBuilder.getRexBuilder().makeInputRef(leftRef.getType(), 0),
                        expectedBuilder.getRexBuilder().makeInputRef(rightRef.getType(), 1)),
                RuleChecks.gt(expectedBuilder,
                        expectedBuilder.getRexBuilder().makeInputRef(leftRef.getType(), 0), 5));
        RelNode expected = org.apache.calcite.rel.logical.LogicalJoin.create(expectedLeft, expectedRight,
                List.of(), joinCondition, java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(new InferFiltersFromConstraints(), before, expected);
    }

    private static void rejectsHandBuiltInequalityJoin() {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        RelNode plan = builder.scan(l).scan(r).join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.AND,
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.joinFields().get(0), builder.joinFields().get(1)),
                        RuleChecks.gt(builder, builder.joinFields().get(0), 5))).build();
        RuleChecks.doesNotApply(new InferFiltersFromConstraints(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running InferFiltersFromConstraints test...");
        runTest();
    }
}
