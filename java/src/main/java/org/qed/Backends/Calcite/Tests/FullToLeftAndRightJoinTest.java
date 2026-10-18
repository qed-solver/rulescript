package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FullToLeftAndRightJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class FullToLeftAndRightJoinTest {
    public static void runTest() {
        var rule = new FullToLeftAndRightJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltEquijoin();
        rejectsInequalityJoin();
    }

    private static void rewritesHandBuiltEquijoin() {
        var source = RuleBuilder.create();
        var leftName = RuleChecks.table(source, 1, false);
        var rightName = RuleChecks.table(source, 1, false);
        var builder = RuleChecks.ruleBuilder(source);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var condition = equality(builder, left, right);
        var before = LogicalJoin.create(left, right, List.of(), condition, java.util.Set.of(), JoinRelType.FULL);

        var expectedLeft = LogicalJoin.create(left, right, List.of(), condition, java.util.Set.of(), JoinRelType.LEFT);
        var rightJoin = LogicalJoin.create(left, right, List.of(), condition, java.util.Set.of(), JoinRelType.RIGHT);
        var unmatched = builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_TRUE, condition);
        var expectedRight = LogicalFilter.create(rightJoin, unmatched);
        var expected = LogicalUnion.create(List.of(expectedLeft, expectedRight), true);
        RuleChecks.rewrites(new FullToLeftAndRightJoin(), before, expected);
    }

    private static RexNode equality(org.apache.calcite.tools.RelBuilder builder,
                                    org.apache.calcite.rel.RelNode left,
                                    org.apache.calcite.rel.RelNode right) {
        var rexBuilder = builder.getRexBuilder();
        var leftValue = rexBuilder.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var rightValue = rexBuilder.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        return rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, leftValue, rightValue);
    }

    private static void rejectsInequalityJoin() {
        var source = RuleBuilder.create();
        var left = RuleChecks.table(source, 1, false);
        var right = RuleChecks.table(source, 1, false);
        var builder = RuleChecks.ruleBuilder(source);
        var before = builder.scan(left).scan(right)
                .join(JoinRelType.FULL, RuleChecks.gt(builder, builder.field(0), 0)).build();

        var independent = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(independent, 1, false);
        var expectedRight = RuleChecks.table(independent, 1, false);
        var expectedBuilder = RuleChecks.ruleBuilder(independent);
        var expected = expectedBuilder.scan(expectedLeft).scan(expectedRight)
                .join(JoinRelType.FULL, RuleChecks.gt(expectedBuilder, expectedBuilder.field(0), 0)).build();
        if (!before.explain().equals(expected.explain())) {
            throw new AssertionError("the separately built no-match plan differs");
        }
        RuleChecks.doesNotApply(new FullToLeftAndRightJoin(), before);
    }

    public static void main(String[] args) {
        System.out.println("Running FullToLeftAndRightJoin test...");
        runTest();
    }
}
