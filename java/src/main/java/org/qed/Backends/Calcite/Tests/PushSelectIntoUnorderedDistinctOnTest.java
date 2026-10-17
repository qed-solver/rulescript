package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.PushSelectIntoUnorderedDistinctOn;
import org.qed.RuleBuilder;

public class PushSelectIntoUnorderedDistinctOnTest {
    public static void runTest() {
        var rule = new PushSelectIntoUnorderedDistinctOn();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoUnorderedDistinctOn rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var input = builder.scan(table).build();
        var beforeBuilder = RuleChecks.ruleBuilder(builder);
        var inputForBefore = beforeBuilder.scan(table).build();
        var ref0 = beforeBuilder.getRexBuilder().makeInputRef(inputForBefore, 0);
        var ref1 = beforeBuilder.getRexBuilder().makeInputRef(inputForBefore, 1);
        var p = beforeBuilder.call(SqlStdOperatorTable.GREATER_THAN, ref0, beforeBuilder.literal(0));
        var q = beforeBuilder.call(SqlStdOperatorTable.LESS_THAN, ref1, beforeBuilder.literal(10));
        var aggregate = LogicalAggregate.create(inputForBefore, java.util.List.of(), ImmutableBitSet.of(0, 1), null,
                java.util.List.of());
        var before = LogicalFilter.create(aggregate, beforeBuilder.and(p, q));

        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        var expectedInput = expectedBuilder.scan(table).build();
        var expectedRef0 = expectedBuilder.getRexBuilder().makeInputRef(expectedInput, 0);
        var expectedRef1 = expectedBuilder.getRexBuilder().makeInputRef(expectedInput, 1);
        var expectedP = expectedBuilder.call(SqlStdOperatorTable.GREATER_THAN, expectedRef0, expectedBuilder.literal(0));
        var expectedQ = expectedBuilder.call(SqlStdOperatorTable.LESS_THAN, expectedRef1, expectedBuilder.literal(10));
        RelNode expected = LogicalAggregate.create(LogicalFilter.create(expectedInput,
                expectedBuilder.and(expectedP, expectedQ)), java.util.List.of(), ImmutableBitSet.of(0, 1), null,
                java.util.List.of());
        RuleChecks.rewrites(rule, before, expected);

        var nullableBuilder = RuleBuilder.create();
        var nullable = RuleChecks.table(nullableBuilder, 2, false, true);
        var nullableInput = nullableBuilder.scan(nullable).build();
        RelNode nullableAggregate = LogicalAggregate.create(nullableInput, java.util.List.of(),
                ImmutableBitSet.of(0, 1), null, java.util.List.of());
        var nullableRef0 = nullableBuilder.getRexBuilder().makeInputRef(nullableAggregate, 0);
        var nullableRef1 = nullableBuilder.getRexBuilder().makeInputRef(nullableAggregate, 1);
        var nullableP = nullableBuilder.call(SqlStdOperatorTable.GREATER_THAN, nullableRef0, nullableBuilder.literal(0));
        var nullableQ = nullableBuilder.call(SqlStdOperatorTable.LESS_THAN, nullableRef1, nullableBuilder.literal(10));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(nullableAggregate, nullableBuilder.and(nullableP, nullableQ)));
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoUnorderedDistinctOn test...");
        runTest();
    }
}
