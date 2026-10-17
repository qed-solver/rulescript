package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.EliminateExistsGroupBy;

public final class EliminateExistsGroupByTest {
    public static void runTest() {
        var rule = new EliminateExistsGroupBy();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        var qedBuilder = org.qed.RuleBuilder.create();
        var outerName = RuleChecks.table(qedBuilder, 1, false);
        var inputName = RuleChecks.table(qedBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        RelNode outer = builder.scan(outerName).build();
        RelNode input = builder.scan(inputName).build();
        var project = org.apache.calcite.rel.logical.LogicalProject.create(input, java.util.List.of(),
                java.util.List.of(builder.getRexBuilder().makeInputRef(input, 0)), java.util.List.of("c0"));
        RelNode grouped = LogicalAggregate.create(project, java.util.List.of(), ImmutableBitSet.of(0), null, java.util.List.of());
        RelNode before = LogicalJoin.create(outer, grouped, java.util.List.of(), builder.getRexBuilder().makeLiteral(true), java.util.Set.of(), JoinRelType.SEMI);
        RelNode expected = LogicalJoin.create(outer, input, java.util.List.of(), builder.getRexBuilder().makeLiteral(true), java.util.Set.of(), JoinRelType.SEMI);
        RuleChecks.rewrites(rule, before, expected);
        RuleChecks.doesNotApply(rule, outer);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateExistsGroupBy test...");
        runTest();
    }
}
