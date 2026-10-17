package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.RewriteDistinctAggregates;
import org.qed.RuleBuilder;

import java.math.BigDecimal;
import java.util.List;

public class RewriteDistinctAggregatesTest {
    public static void runTest() {
        var rule = new RewriteDistinctAggregates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RewriteDistinctAggregates rule) {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false);
        var b = RuleChecks.ruleBuilder(qed);
        var rex = b.getRexBuilder();
        var scan = b.scan(table).build();
        var zero = rex.makeBigintLiteral(BigDecimal.ZERO);

        var project = LogicalProject.create(scan, List.of(), List.of(zero), List.of("c"));
        var distinct = AggregateCall.create(SqlStdOperatorTable.COUNT, true, false, false, List.of(0), -1, null,
                RelCollations.EMPTY, 1, project, null, "n");
        var before = LogicalAggregate.create(project, List.of(), ImmutableBitSet.of(), null, List.of(distinct));

        var e = RuleChecks.ruleBuilder(qed);
        e.scan(table);
        e.project(List.of(zero, e.literal(0)), List.of(), true);
        e.aggregate(e.groupKey(zero, e.literal(0)));
        e.aggregate(e.groupKey(), e.count(false, "n", zero));
        RuleChecks.rewrites(rule, before, e.build());

        var plain = AggregateCall.create(SqlStdOperatorTable.COUNT, false, false, false, List.of(0), -1, null,
                RelCollations.EMPTY, 1, project, null, "n");
        RuleChecks.doesNotApply(rule,
                LogicalAggregate.create(project, List.of(), ImmutableBitSet.of(), null, List.of(plain)));

        var one = LogicalProject.create(scan, List.of(), List.of(rex.makeBigintLiteral(BigDecimal.ONE)), List.of("c"));
        var otherDistinct = AggregateCall.create(SqlStdOperatorTable.COUNT, true, false, false, List.of(0), -1, null,
                RelCollations.EMPTY, 1, one, null, "n");
        RuleChecks.doesNotApply(rule,
                LogicalAggregate.create(one, List.of(), ImmutableBitSet.of(), null, List.of(otherDistinct)));
    }

    public static void main(String[] args) {
        System.out.println("Running RewriteDistinctAggregates test...");
        runTest();
    }
}
