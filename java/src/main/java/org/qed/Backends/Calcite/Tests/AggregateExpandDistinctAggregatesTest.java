package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.RRuleInstances.AggregateExpandDistinctAggregates;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateExpandDistinctAggregatesTest {
    public static void runTest() {
        var rule = new AggregateExpandDistinctAggregates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static RelNode keyedProject(RelNode scan) {
        var rex = scan.getCluster().getRexBuilder();
        var x = rex.makeInputRef(scan, 0);
        var k = rex.makeCall(SqlStdOperatorTable.PLUS, x, rex.makeExactLiteral(java.math.BigDecimal.ONE));
        return LogicalProject.create(scan, List.of(), List.of(k, x), List.of("k", "x"));
    }

    private static AggregateCall call(org.apache.calcite.sql.SqlAggFunction function, boolean distinct, RelNode input,
                                      String name) {
        return AggregateCall.create(function, distinct, false, false, List.of(1), -1, null, RelCollations.EMPTY, 1,
                input, null, name);
    }

    private static void ordinaryTables(AggregateExpandDistinctAggregates rule) {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, false);
        var b = RuleChecks.ruleBuilder(qed);
        var input = keyedProject(b.scan(table).build());
        var before = LogicalAggregate.create(input, List.of(), ImmutableBitSet.of(0), null, List.of(
                call(SqlStdOperatorTable.COUNT, true, input, "f"), call(SqlStdOperatorTable.SUM, true, input, "g")));

        var e = RuleChecks.ruleBuilder(qed);
        e.scan(table);
        e.project(List.of(e.field(0), e.call(SqlStdOperatorTable.PLUS, e.field(0), e.literal(1)), e.field(0)),
                List.of(), true);
        e.aggregate(e.groupKey(1, 2));
        e.aggregate(e.groupKey(0), e.count(false, "f"), e.sum(false, "g", e.field(1)));
        var after = e.build();
        RuleChecks.rewrites(rule, before, after);

        var mixed = LogicalAggregate.create(input, List.of(), ImmutableBitSet.of(0), null, List.of(
                call(SqlStdOperatorTable.COUNT, true, input, "f"), call(SqlStdOperatorTable.SUM, false, input, "g")));
        RuleChecks.doesNotApply(rule, mixed);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateExpandDistinctAggregates test...");
        runTest();
    }
}
