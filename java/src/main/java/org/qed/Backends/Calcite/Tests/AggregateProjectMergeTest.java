package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelType;
import org.qed.RRuleInstances.AggregateProjectMerge;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateProjectMergeTest {
    public static void runTest() {
        var rule = new AggregateProjectMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesConcreteAggregate(rule);
        rejectsOuterJoin(rule);
    }

    private static void rewritesConcreteAggregate(AggregateProjectMerge rule) {
        var tables = RuleBuilder.create();
        var leftName = RuleChecks.table(tables, 1, false);
        var rightName = RuleChecks.table(tables, 1, false);
        var builder = RuleChecks.ruleBuilder(tables);
        builder.scan(leftName).scan(rightName).join(JoinRelType.INNER, builder.literal(true));
        var base = builder.build();
        builder.push(base).project(List.of(builder.field(1), builder.field(0)), List.of(), true);
        var projected = builder.build();
        var before = aggregate(projected, 0);
        var after = aggregate(base, 1);
        RuleChecks.rewrites(rule, before, after);
    }

    private static void rejectsOuterJoin(AggregateProjectMerge rule) {
        var tables = RuleBuilder.create();
        var leftName = RuleChecks.table(tables, 1, false);
        var rightName = RuleChecks.table(tables, 1, false);
        var builder = RuleChecks.ruleBuilder(tables);
        builder.scan(leftName).scan(rightName).join(JoinRelType.LEFT, builder.literal(true));
        var base = builder.build();
        builder.push(base).project(List.of(builder.field(1), builder.field(0)), List.of(), true);
        var projected = builder.build();
        RuleChecks.doesNotApply(rule, aggregate(projected, 0));
    }

    private static RelNode aggregate(RelNode input, int ordinal) {
        var builder = RuleBuilder.create();
        var groups = ImmutableBitSet.of(ordinal);
        var call = AggregateCall.create(builder.genericAggregateOp("f", new RelType.VarType("f_type", true)),
                false, false, false, List.of(ordinal), -1, null, RelCollations.EMPTY, groups.cardinality(), input,
                null, "f");
        return LogicalAggregate.create(input, List.of(), groups, null, List.of(call));
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateProjectMerge test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError(CalciteTester.failures() + " test failures");
        }
    }
}
