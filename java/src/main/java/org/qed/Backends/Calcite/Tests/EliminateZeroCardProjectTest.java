package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateZeroCardProject;
import org.qed.RuleBuilder;

import java.util.List;

public class EliminateZeroCardProjectTest {
    public static void runTest() {
        var rule = new EliminateZeroCardProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltProjects();
        rejectsHandBuiltNearMisses();
    }

    private static void rewritesHandBuiltProjects() {
        var builder = RuleChecks.ruleBuilder(RuleBuilder.create());
        var input = emptyInput(builder);
        builder.push(input);
        var first = builder.field(0);
        var second = builder.field(1);
        var projected = builder.call(SqlStdOperatorTable.PLUS, first, builder.literal(7));
        RelNode before = builder.project(List.of(first, second, projected)).build();

        var expectedBuilder = RuleChecks.ruleBuilder(RuleBuilder.create());
        var expected = LogicalValues.create(expectedBuilder.getCluster(), before.getRowType(),
                com.google.common.collect.ImmutableList.of());
        RuleChecks.rewrites(new EliminateZeroCardProject(), before, expected);
    }

    private static void rejectsHandBuiltNearMisses() {
        var builder = RuleChecks.ruleBuilder(RuleBuilder.create());
        var input = emptyInput(builder);
        builder.push(input);
        var first = builder.field(0);
        var second = builder.field(1);
        var projected = builder.call(SqlStdOperatorTable.PLUS, first, builder.literal(7));
        var rule = new EliminateZeroCardProject();

        RelNode wrongFirstPassthrough = builder.project(List.of(second, second, projected)).build();
        RuleChecks.doesNotApply(rule, wrongFirstPassthrough);

        var otherBuilder = RuleChecks.ruleBuilder(RuleBuilder.create());
        RelNode nonEmptyInput = otherBuilder.values(new String[]{"c0", "c1"}, 1, 2).build();
        otherBuilder.push(nonEmptyInput);
        var nonEmptyFirst = otherBuilder.field(0);
        var nonEmptySecond = otherBuilder.field(1);
        var nonEmptyProjected = otherBuilder.call(SqlStdOperatorTable.PLUS, nonEmptyFirst, otherBuilder.literal(7));
        RelNode nonEmptyProject = otherBuilder.project(List.of(nonEmptyFirst, nonEmptySecond, nonEmptyProjected)).build();
        RuleChecks.doesNotApply(rule, nonEmptyProject);
    }

    private static RelNode emptyInput(RelBuilder builder) {
        var base = builder.values(new String[]{"c0", "c1"}, 1, 2).build();
        var empty = LogicalValues.create(base.getCluster(), base.getRowType(),
                com.google.common.collect.ImmutableList.of());
        return empty;
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateZeroCardProject test...");
        runTest();
    }
}
