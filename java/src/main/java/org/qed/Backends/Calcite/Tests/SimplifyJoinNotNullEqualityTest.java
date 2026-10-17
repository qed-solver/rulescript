package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyJoinNotNullEquality;

import java.math.BigDecimal;
import java.util.List;
import com.google.common.collect.ImmutableList;

public final class SimplifyJoinNotNullEqualityTest {
    public static void runTest() {
        var rule = new SimplifyJoinNotNullEquality();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        independentCase();
        meaningfulNoMatch();
    }

    private static void independentCase() {
        var b = RuleChecks.ruleBuilder(org.qed.RuleBuilder.create());
        var leftType = b.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.INTEGER);
        var rightType = b.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.INTEGER);
        RelNode left = LogicalValues.create(b.getCluster(), b.getTypeFactory().builder().add("l", leftType).build(),
                ImmutableList.of(ImmutableList.of(b.getRexBuilder().makeExactLiteral(BigDecimal.ONE, leftType))));
        var right = LogicalValues.create(b.getCluster(), b.getTypeFactory().builder().add("r", rightType).build(),
                ImmutableList.of(ImmutableList.of(b.getRexBuilder().makeExactLiteral(BigDecimal.ONE, rightType))));
        var refs = new RexNode[]{b.getRexBuilder().makeInputRef(leftType, 0),
                b.getRexBuilder().makeInputRef(rightType, 1)};
        var eq = b.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS, refs);
        var isNotFalse = b.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_FALSE, eq);
        var rest = b.getRexBuilder().makeCall(SqlStdOperatorTable.GREATER_THAN, refs[0],
                b.getRexBuilder().makeExactLiteral(BigDecimal.ZERO, leftType));
        var before = LogicalJoin.create(left, right,
                List.of(), b.getRexBuilder().makeCall(SqlStdOperatorTable.AND, isNotFalse, rest),
                java.util.Set.of(), JoinRelType.INNER);
        var expected = LogicalJoin.create(left, right, List.of(),
                b.getRexBuilder().makeCall(SqlStdOperatorTable.AND, eq, rest), java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(new SimplifyJoinNotNullEquality(), before, expected);
    }

    private static void meaningfulNoMatch() {
        var b = RuleChecks.ruleBuilder(org.qed.RuleBuilder.create());
        var leftType = b.getTypeFactory().createTypeWithNullability(
                b.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.INTEGER), true);
        var rightType = b.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.INTEGER);
        var left = LogicalValues.create(b.getCluster(), b.getTypeFactory().builder().add("l", leftType).build(),
                ImmutableList.of(ImmutableList.of(b.getRexBuilder().makeNullLiteral(leftType))));
        var right = LogicalValues.create(b.getCluster(), b.getTypeFactory().builder().add("r", rightType).build(),
                ImmutableList.of(ImmutableList.of(b.getRexBuilder().makeExactLiteral(BigDecimal.ONE, rightType))));
        var refs = new RexNode[]{b.getRexBuilder().makeInputRef(leftType, 0),
                b.getRexBuilder().makeInputRef(rightType, 1)};
        var eq = b.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS, refs);
        var condition = b.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_FALSE, eq);
        var plan = LogicalJoin.create(left, right, List.of(), condition,
                java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.doesNotApply(new SimplifyJoinNotNullEquality(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyJoinNotNullEquality test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("Calcite test failures: " + CalciteTester.failures());
        }
    }
}
