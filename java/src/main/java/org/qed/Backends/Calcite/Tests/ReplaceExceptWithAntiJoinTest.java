package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalMinus;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import java.util.Set;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ReplaceExceptWithAntiJoin;

import java.util.List;

public class ReplaceExceptWithAntiJoinTest {
    public static void runTest() {
        var rule = new ReplaceExceptWithAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 2, false, true);
        var rightName = RuleChecks.table(builder, 2, false, true);
        var calcite = RuleChecks.ruleBuilder(builder);
        var left = calcite.scan(leftName).build();
        var right = calcite.scan(rightName).build();
        var input = LogicalMinus.create(List.of(left, right), false);

        var rex = new RexBuilder(calcite.getTypeFactory());
        var condition = rex.makeCall(SqlStdOperatorTable.AND,
                rex.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                        rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                        rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 2)),
                rex.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                        rex.makeInputRef(left.getRowType().getFieldList().get(1).getType(), 1),
                        rex.makeInputRef(right.getRowType().getFieldList().get(1).getType(), 3)));
        RelNode anti = LogicalJoin.create(left, right, List.of(), condition, Set.of(), JoinRelType.ANTI);
        var groupSet = ImmutableBitSet.of(0, 1);
        var expected = LogicalAggregate.create(anti, List.of(), groupSet, null,
                List.<AggregateCall>of());
        RuleChecks.rewrites(rule, input, expected);

        var all = LogicalMinus.create(List.of(left, right), true);
        RuleChecks.doesNotApply(rule, all);
    }

    public static void main(String[] args) {
        System.out.println("Running ReplaceExceptWithAntiJoin test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
