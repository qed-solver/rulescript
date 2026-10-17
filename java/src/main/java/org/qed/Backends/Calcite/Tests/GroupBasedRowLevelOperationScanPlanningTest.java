package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.GroupBasedRowLevelOperationScanPlanning;
import org.qed.RuleBuilder;

public class GroupBasedRowLevelOperationScanPlanningTest {
    public static void runTest() {
        var rule = new GroupBasedRowLevelOperationScanPlanning();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPushdown();
        rejectsNonInnerJoin();
    }

    private static void handBuiltPushdown() {
        var factory = RuleBuilder.create();
        var target = RuleChecks.table(factory, 1, false);
        var other = RuleChecks.table(factory, 1, false);
        var planBuilder = RuleChecks.ruleBuilder(factory);
        var targetScan = planBuilder.scan(target).build();
        var otherScan = planBuilder.scan(other).build();
        var targetRef = ref(planBuilder, targetScan, 0);
        var otherRef = ref(planBuilder, otherScan, 1);
        var eval1 = RuleChecks.gt(planBuilder, targetRef, 10);
        var eval2 = RuleChecks.gt(planBuilder, targetRef, 20);
        var rest = RuleChecks.eq(planBuilder, targetRef, otherRef);
        RelNode before = planBuilder.push(targetScan).push(otherScan)
                .join(JoinRelType.INNER, planBuilder.call(SqlStdOperatorTable.AND, eval1, eval2, rest)).build();

        var expectedBuilder = RuleChecks.ruleBuilder(factory);
        var expectedTarget = expectedBuilder.scan(target).filter(
                RuleChecks.gt(expectedBuilder, ref(expectedBuilder, expectedBuilder.peek(), 0), 10),
                RuleChecks.gt(expectedBuilder, ref(expectedBuilder, expectedBuilder.peek(), 0), 20)).build();
        var expectedOther = expectedBuilder.scan(other).build();
        var expectedTargetRef = ref(expectedBuilder, expectedTarget, 0);
        var expectedOtherRef = ref(expectedBuilder, expectedOther, 1);
        var expectedRest = RuleChecks.eq(expectedBuilder, expectedTargetRef, expectedOtherRef);
        RelNode expected = expectedBuilder.push(expectedTarget).push(expectedOther)
                .join(JoinRelType.INNER, expectedRest).build();
        RuleChecks.rewrites(rule(), before, expected);
    }

    private static void rejectsNonInnerJoin() {
        var factory = RuleBuilder.create();
        var target = RuleChecks.table(factory, 1, false);
        var other = RuleChecks.table(factory, 1, false);
        var planBuilder = RuleChecks.ruleBuilder(factory);
        var targetScan = planBuilder.scan(target).build();
        var otherScan = planBuilder.scan(other).build();
        var targetRef = ref(planBuilder, targetScan, 0);
        RexNode eval1 = RuleChecks.gt(planBuilder, targetRef, 10);
        RexNode eval2 = RuleChecks.gt(planBuilder, targetRef, 20);
        RexNode rest = RuleChecks.eq(planBuilder, targetRef, ref(planBuilder, otherScan, 1));
        var plan = planBuilder.push(targetScan).push(otherScan)
                .join(JoinRelType.LEFT, planBuilder.call(SqlStdOperatorTable.AND, eval1, eval2, rest)).build();
        RuleChecks.doesNotApply(rule(), plan);
    }

    private static GroupBasedRowLevelOperationScanPlanning rule() {
        return new GroupBasedRowLevelOperationScanPlanning();
    }

    private static RexNode ref(RelBuilder builder, RelNode input, int index) {
        return builder.getRexBuilder().makeInputRef(
                input.getRowType().getFieldList().get(index % input.getRowType().getFieldCount()).getType(), index);
    }

    public static void main(String[] args) {
        System.out.println("Running GroupBasedRowLevelOperationScanPlanning test...");
        runTest();
    }
}
