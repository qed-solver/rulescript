package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexInputRef;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SemiJoinRemove;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public class SemiJoinRemoveTest {
    public static void runTest() {
        var rule = new SemiJoinRemove();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        handBuiltRepeatedRightAndCondition(rule);
        rejectsDifferentOuterCondition(rule);
    }

    private static void handBuiltRepeatedRightAndCondition(SemiJoinRemove rule) {
        var builder = RuleBuilder.create();
        var leftName = table(builder, 1, false, false);
        var rightName = table(builder, 1, false, false);
        var b = builder.transform(CalciteRuntime::configure);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var rex = b.getRexBuilder();
        var condition = rex.makeCall(SqlStdOperatorTable.EQUALS,
                rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));
        RelNode semi = LogicalJoin.create(left, right, List.of(), condition, Set.of(), JoinRelType.SEMI);
        RelNode before = LogicalJoin.create(semi, right, List.of(), condition, Set.of(), JoinRelType.INNER);
        RelNode expected = LogicalJoin.create(left, right, List.of(), condition, Set.of(), JoinRelType.INNER);
        rewrites(rule, before, expected);
    }

    private static void rejectsDifferentOuterCondition(SemiJoinRemove rule) {
        var builder = RuleBuilder.create();
        var leftName = table(builder, 1, false, false);
        var rightName = table(builder, 1, false, false);
        var b = builder.transform(CalciteRuntime::configure);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var rex = b.getRexBuilder();
        var semiCondition = rex.makeCall(SqlStdOperatorTable.EQUALS,
                rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));
        var outerCondition = rex.makeCall(SqlStdOperatorTable.GREATER_THAN,
                rex.makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));
        RelNode semi = LogicalJoin.create(left, right, List.of(), semiCondition, Set.of(), JoinRelType.SEMI);
        RelNode before = LogicalJoin.create(semi, right, List.of(), outerCondition, Set.of(), JoinRelType.INNER);
        doesNotApply(rule, before);
    }

    public static void main(String[] args) {
        System.out.println("Running SemiJoinRemove test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("SemiJoinRemove had " + CalciteTester.failures() + " failure(s)");
        }
    }
}
