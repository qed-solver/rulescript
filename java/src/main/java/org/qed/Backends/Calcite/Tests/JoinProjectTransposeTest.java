package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.JoinProjectTranspose;
import org.qed.RuleBuilder;

public class JoinProjectTransposeTest {
    public static void runTest() {
        var rule = new JoinProjectTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
        handBuiltRewrite();
        handBuiltNearMiss();
    }

    private static void ordinaryTables(JoinProjectTranspose rule) {
        var builder = RuleBuilder.create();
        RuleChecks.table(builder, 2, false);
        RuleChecks.table(builder, 2, false);
        var before = FaithfulPlans.of(rule.before());
        var after = FaithfulPlans.expected(rule.after());
        RuleChecks.rewrites(rule, before, after);
    }

    private static void handBuiltRewrite() {
        var rule = new JoinProjectTranspose();
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 2, false);
        var right = RuleChecks.table(builder, 2, false);
        var before = FaithfulPlans.of(rule.before());
        var expected = FaithfulPlans.expected(rule.after());
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void handBuiltNearMiss() {
        var builder = RuleBuilder.create();
        RuleChecks.table(builder, 2, false);
        RuleChecks.table(builder, 2, false);
        var matching = FaithfulPlans.of(new JoinProjectTranspose().before());
        var join = (org.apache.calcite.rel.logical.LogicalJoin) matching;
        var plan = org.apache.calcite.rel.logical.LogicalJoin.create(join.getLeft(), join.getRight(),
                join.getHints(), join.getCondition(), join.getVariablesSet(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(new JoinProjectTranspose(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running JoinProjectTranspose test...");
        runTest();
    }
}
