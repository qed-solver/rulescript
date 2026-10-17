package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.RemoveRepetitionFromGroupExpressions;
import org.qed.RuleBuilder;

import java.util.List;

public final class RemoveRepetitionFromGroupExpressionsTest {
    public static void runTest() {
        var rule = new RemoveRepetitionFromGroupExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RemoveRepetitionFromGroupExpressions rule) {
        var schema = RuleBuilder.create();
        var tableName = RuleChecks.table(schema, 3, false, true);
        var b = RuleChecks.ruleBuilder(schema);
        var scan = b.scan(tableName).build();
        var duplicated = LogicalProject.create(scan, List.of(), List.of(
                b.getRexBuilder().makeInputRef(scan, 0), b.getRexBuilder().makeInputRef(scan, 1),
                b.getRexBuilder().makeInputRef(scan, 0), b.getRexBuilder().makeInputRef(scan, 2)), (List<String>) null);
        var before = aggregate(b, duplicated, ImmutableBitSet.of(0, 1, 2), 3, true);
        var projected = LogicalProject.create(before, List.of(), List.of(
                b.getRexBuilder().makeInputRef(before, 0), b.getRexBuilder().makeInputRef(before, 1),
                b.getRexBuilder().makeInputRef(before, 3)), (List<String>) null);
        var expected = aggregate(b, scan, ImmutableBitSet.of(0, 1), 2, false);
        RuleChecks.rewrites(rule, projected, expected);

        // This otherwise identical hand-built plan groups by the second source column twice.
        var wrongDuplicate = LogicalProject.create(scan, List.of(), List.of(
                b.getRexBuilder().makeInputRef(scan, 0), b.getRexBuilder().makeInputRef(scan, 1),
                b.getRexBuilder().makeInputRef(scan, 1), b.getRexBuilder().makeInputRef(scan, 2)), (List<String>) null);
        var nearMissAggregate = aggregate(b, wrongDuplicate, ImmutableBitSet.of(0, 1, 2), 3, true);
        var nearMiss = LogicalProject.create(nearMissAggregate, List.of(), List.of(
                b.getRexBuilder().makeInputRef(nearMissAggregate, 0),
                b.getRexBuilder().makeInputRef(nearMissAggregate, 1),
                b.getRexBuilder().makeInputRef(nearMissAggregate, 3)), (List<String>) null);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static LogicalAggregate aggregate(RelBuilder b, RelNode input, ImmutableBitSet groups,
                                              int operand, boolean nullable) {
        var call = org.apache.calcite.rel.core.AggregateCall.create(
                org.apache.calcite.sql.fun.SqlStdOperatorTable.SUM, false, false, false,
                List.of(operand), -1, null, RelCollations.EMPTY, groups.cardinality(), input, null, "sum");
        return LogicalAggregate.create(input, List.of(), groups, null, List.of(call));
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveRepetitionFromGroupExpressions test...");
        runTest();
    }
}
