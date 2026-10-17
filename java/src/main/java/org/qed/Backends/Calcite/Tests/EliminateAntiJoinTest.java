package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalValues;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateAntiJoin;
import org.qed.RuleBuilder;

public class EliminateAntiJoinTest {
    public static void runTest() {
        var rule = new EliminateAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var left = b.scan(leftName).build();
        var rightName = RuleChecks.table(builder, 1, false);
        var emptyRightScan = b.scan(rightName).build();
        var emptyRight = LogicalValues.create(emptyRightScan.getCluster(), emptyRightScan.getRowType(), com.google.common.collect.ImmutableList.of());
        var before = b.scan(leftName).push(emptyRight).antiJoin(b.literal(true)).build();
        RelNode expected = b.scan(leftName).build();
        RuleChecks.rewrites(rule, before, expected);

        var missSource = RuleBuilder.create();
        var nonemptyName = RuleChecks.table(missSource, 1, false);
        var missBuilder = RuleChecks.ruleBuilder(missSource);
        var nonempty = missBuilder.scan(nonemptyName).build();
        var missRight = LogicalValues.create(nonempty.getCluster(), nonempty.getRowType(), com.google.common.collect.ImmutableList.of(
                com.google.common.collect.ImmutableList.of(nonempty.getCluster().getRexBuilder().makeExactLiteral(java.math.BigDecimal.ONE))));
        RelNode nonemptyAntiJoin = missBuilder.scan(nonemptyName).push(missRight).antiJoin(missBuilder.literal(true)).build();
        RuleChecks.doesNotApply(rule, nonemptyAntiJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAntiJoin test...");
        runTest();
    }
}
