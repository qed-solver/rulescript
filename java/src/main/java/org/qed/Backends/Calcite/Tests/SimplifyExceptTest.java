package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalMinus;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyExcept;

import java.util.List;

public class SimplifyExceptTest {
    public static void runTest() {
        var rule = new SimplifyExcept();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        handBuiltRewrite();
        rejectsExceptAll();
    }

    private static void handBuiltRewrite() {
        var qedBuilder = org.qed.RuleBuilder.create();
        var leftName = RuleChecks.table(qedBuilder, 1, true, false);
        var rightName = RuleChecks.table(qedBuilder, 1, false, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        var source = LogicalMinus.create(List.of(left, right), false);

        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM,
                builder.getRexBuilder().makeInputRef(left.getRowType().getFieldList().get(0).getType(), 0),
                builder.getRexBuilder().makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));
        var expected = org.apache.calcite.rel.logical.LogicalJoin.create(left, right, List.of(), condition,
                java.util.Set.of(), JoinRelType.ANTI);
        RuleChecks.rewrites(new SimplifyExcept(), source, expected);
    }

    private static void rejectsExceptAll() {
        var qedBuilder = org.qed.RuleBuilder.create();
        var leftName = RuleChecks.table(qedBuilder, 1, true, false);
        var rightName = RuleChecks.table(qedBuilder, 1, false, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        var left = builder.scan(leftName).build();
        var right = builder.scan(rightName).build();
        RuleChecks.doesNotApply(new SimplifyExcept(), LogicalMinus.create(List.of(left, right), true));
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyExcept test...");
        runTest();
    }
}
