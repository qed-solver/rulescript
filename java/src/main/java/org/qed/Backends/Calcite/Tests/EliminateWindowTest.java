package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateWindow;

import java.util.List;

public class EliminateWindowTest {
    public static void runTest() {
        var rule = new EliminateWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive();
        rejectsUnrelatedAggregate();
    }

    private static void handBuiltPositive() {
        var builder = org.qed.RuleBuilder.create();
        var input = RuleChecks.table(builder, 2, false, false);
        var scan = builder.scan(input).build();
        var key = scan.getRowType().getFieldList().get(0).getType();
        var value = scan.getRowType().getFieldList().get(1).getType();
        var aggregate = LogicalAggregate.create(scan, List.of(), ImmutableBitSet.of(0), null, List.of());
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                builder.getRexBuilder().makeInputRef(key, 0), builder.getRexBuilder().makeInputRef(key, 2));
        var joined = LogicalJoin.create(scan, aggregate, List.of(), condition,
                java.util.Set.of(), org.apache.calcite.rel.core.JoinRelType.INNER);
        var before = LogicalProject.create(joined, List.of(),
                List.of(builder.getRexBuilder().makeInputRef(key, 0),
                        builder.getRexBuilder().makeInputRef(value, 1)), List.of("k", "v"));
        RuleChecks.rewrites(new EliminateWindow(), before, scan);
        System.out.println("succeeded (hand-built window elimination plan)");
    }

    private static void rejectsUnrelatedAggregate() {
        var builder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var scan = builder.scan(table).build();
        RelNode unrelated = LogicalAggregate.create(scan, List.of(), ImmutableBitSet.of(1), null, List.of());
        RuleChecks.doesNotApply(new EliminateWindow(), unrelated);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateWindow test...");
        runTest();
    }
}
