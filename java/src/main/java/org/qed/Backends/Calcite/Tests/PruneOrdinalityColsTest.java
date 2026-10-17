package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.qed.RRuleInstances.PruneOrdinalityCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public final class PruneOrdinalityColsTest {
    private PruneOrdinalityColsTest() {}

    public static void runTest() {
        var rule = new PruneOrdinalityCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneOrdinalityCols rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 3, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        var input = relBuilder.scan(table).build();
        var rexBuilder = relBuilder.getRexBuilder();
        var ordinality = RuleBuilder.create().genericProjectionOp("ORDINALITY", RelType.fromString("INTEGER", true));
        var outer = RuleBuilder.create().genericProjectionOp("OUTPUT", RelType.fromString("INTEGER", true));

        var beforeFields = fields(input, 0, 1, 2);
        var beforeOrdinality = call(rexBuilder, ordinality, fields(input, 0, 1));
        var beforeOrdinalityProject = project(input, append(beforeFields, beforeOrdinality));
        var before = project(beforeOrdinalityProject,
                List.of(call(rexBuilder, outer, List.of(
                        ref(rexBuilder, beforeOrdinalityProject, 0), ref(rexBuilder, beforeOrdinalityProject, 1),
                        ref(rexBuilder, beforeOrdinalityProject, 3)))));

        var pruned = project(input, fields(input, 0, 1));
        var afterOrdinality = call(rexBuilder, ordinality, fields(pruned, 0, 1));
        var afterOrdinalityProject = project(pruned, append(fields(pruned, 0, 1), afterOrdinality));
        var after = project(afterOrdinalityProject,
                List.of(call(rexBuilder, outer, List.of(
                        ref(rexBuilder, afterOrdinalityProject, 0), ref(rexBuilder, afterOrdinalityProject, 1),
                        ref(rexBuilder, afterOrdinalityProject, 2)))));
        RuleChecks.rewrites(rule, before, after);

        var wrongOuter = project(beforeOrdinalityProject,
                List.of(call(rexBuilder, outer, List.of(
                        ref(rexBuilder, beforeOrdinalityProject, 0), ref(rexBuilder, beforeOrdinalityProject, 1),
                        ref(rexBuilder, beforeOrdinalityProject, 2)))));
        RuleChecks.doesNotApply(rule, wrongOuter);
    }

    private static List<RexNode> fields(RelNode input, int... ordinals) {
        return java.util.Arrays.stream(ordinals)
                .<RexNode>mapToObj(i -> input.getCluster().getRexBuilder().makeInputRef(input, i)).toList();
    }

    private static RexNode ref(org.apache.calcite.rex.RexBuilder rexBuilder, RelNode input, int ordinal) {
        return rexBuilder.makeInputRef(input, ordinal);
    }

    private static RexNode call(org.apache.calcite.rex.RexBuilder rexBuilder, SqlOperator operator,
                                List<RexNode> arguments) {
        return rexBuilder.makeCall(operator, arguments);
    }

    private static List<RexNode> append(List<RexNode> fields, RexNode extra) {
        var out = new java.util.ArrayList<>(fields);
        out.add(extra);
        return out;
    }

    private static RelNode project(RelNode input, List<RexNode> expressions) {
        return LogicalProject.create(input, List.of(), expressions,
                java.util.stream.IntStream.range(0, expressions.size()).mapToObj(i -> "c" + i).toList());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneOrdinalityCols test...");
        runTest();
    }
}
