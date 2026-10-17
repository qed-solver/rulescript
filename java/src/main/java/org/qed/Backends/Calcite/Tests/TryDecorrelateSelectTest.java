package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.TryDecorrelateSelect;

public class TryDecorrelateSelectTest {
    public static void runTest() {
        var rule = new TryDecorrelateSelect();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false);
        var rightName = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var leftRef = b.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0);
        var rightRef = b.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);
        var on = RuleChecks.eq(b, leftRef, rightRef);
        var selectedRight = org.apache.calcite.rel.logical.LogicalFilter.create(right,
                RuleChecks.gt(b, b.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 0), 10));
        var handBuilt = org.apache.calcite.rel.logical.LogicalJoin.create(left, selectedRight, java.util.List.of(), on,
                java.util.Set.of(), JoinRelType.INNER);
        var expected = org.apache.calcite.rel.logical.LogicalJoin.create(left, right, java.util.List.of(),
                b.call(SqlStdOperatorTable.AND, on, RuleChecks.gt(b,
                        b.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1), 10)),
                java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, handBuilt, expected);

        var wrongJoin = org.apache.calcite.rel.logical.LogicalJoin.create(left, selectedRight, java.util.List.of(), on,
                java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, wrongJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateSelect test...");
        runTest();
    }
}
