package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ReduceWindowPartitionCols;
import org.qed.RuleBuilder;

public class ReduceWindowPartitionColsTest {

    public static void runTest() {
        var rule = new ReduceWindowPartitionCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ReduceWindowPartitionCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var input = b.scan(t).build();
        var win = b.push(input)
                .project(b.field(0), b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0)), b.field(1)).build();
        var partFull = b.push(win).aggregate(b.groupKey(0, 1), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(2))).build();
        var partReduced = b.push(win).aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(2)))
                .project(b.field(0), b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0)), b.field(1)).build();
        var cond = (java.util.function.Supplier<RexNode>) () -> b.and(
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                RuleChecks.eq(b, b.field(2, 0, 1), b.field(2, 1, 1)));
        var top = (java.util.function.Supplier<RexNode>) () -> b.call(SqlStdOperatorTable.PLUS,
                b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)),
                b.call(SqlStdOperatorTable.PLUS, b.field(2), b.field(5)));
        var before = b.push(win).push(partFull).join(JoinRelType.INNER, cond.get()).project(top.get()).build();
        var after = b.push(win).push(partReduced).join(JoinRelType.INNER, cond.get()).project(top.get()).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.push(win).push(partFull).join(JoinRelType.LEFT, cond.get()).project(top.get()).build());
        RuleChecks.doesNotApply(rule, b.push(win).push(partReduced).join(JoinRelType.INNER, cond.get()).project(top.get()).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ReduceWindowPartitionCols test...");
        runTest();
    }
}
