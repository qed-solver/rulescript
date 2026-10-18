package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.qed.RRuleInstances.JoinOnUniqueToSemiJoin;
import org.qed.RuleBuilder;

public class JoinOnUniqueToSemiJoinTest {
    public static void runTest() {
        var rule = new JoinOnUniqueToSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);

        var beforeSchema = RuleBuilder.create();
        var left = RuleChecks.table(beforeSchema, 1, false);
        var right = RuleChecks.table(beforeSchema, 1, true);
        var builder = RuleChecks.ruleBuilder(beforeSchema);
        var before = joinedPlan(builder, left, right, JoinRelType.INNER, false);

        var expectedSchema = RuleBuilder.create();
        var expectedLeft = RuleChecks.table(expectedSchema, 1, false);
        var expectedRight = RuleChecks.table(expectedSchema, 1, true);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedSchema);
        var expected = joinedPlan(expectedBuilder, expectedLeft, expectedRight, JoinRelType.SEMI, false);
        RuleChecks.rewrites(rule, before, expected);

        var nearSchema = RuleBuilder.create();
        var nearLeft = RuleChecks.table(nearSchema, 1, false);
        var nearRight = RuleChecks.table(nearSchema, 1, true);
        var nearBuilder = RuleChecks.ruleBuilder(nearSchema);
        var reversedCondition = joinedPlan(nearBuilder, nearLeft, nearRight, JoinRelType.INNER, true);
        RuleChecks.doesNotApply(rule, reversedCondition);
    }

    private static void ordinaryTables(JoinOnUniqueToSemiJoin rule) {
        var schema = RuleBuilder.create();
        var left = RuleChecks.table(schema, 1, false);
        var right = RuleChecks.table(schema, 1, false);
        var builder = RuleChecks.ruleBuilder(schema);
        var plan = joinedPlan(builder, left, right, JoinRelType.INNER, false);
        RuleChecks.doesNotApply(rule, plan);
    }

    private static org.apache.calcite.rel.RelNode joinedPlan(org.apache.calcite.tools.RelBuilder builder,
            String leftName, String rightName, JoinRelType joinType, boolean reversed) {
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        builder.push(left).push(right);
        var leftRef = builder.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(),
                reversed ? 1 : 0);
        var rightRef = builder.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(),
                reversed ? 0 : 1);
        RexNode condition = builder.getRexBuilder().makeCall(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS, leftRef, rightRef);
        var join = builder.join(joinType, condition).build();
        builder.push(join);
        var output = builder.getRexBuilder().makeInputRef(join.getRowType().getFieldList().get(0).getType(), 0);
        return builder.project(java.util.List.of(output)).build();
    }

    public static void main(String[] args) {
        System.out.println("Running JoinOnUniqueToSemiJoin test...");
        runTest();
    }
}
