package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.EliminateJoinNoColsRight;
import org.qed.RuleBuilder;

public final class EliminateJoinNoColsRightTest {
    public static void runTest() {
        var rule = new EliminateJoinNoColsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive(rule);
        rejectsMultipleRightRows(rule);
    }

    private static void handBuiltPositive(EliminateJoinNoColsRight rule) {
        var raw = RuleBuilder.create();
        var leftName = RuleChecks.table(raw, 1, false);
        var b = raw.transform(CalciteRuntime::configure);
        var left = b.scan(leftName).build();
        var oneBase = b.values(new String[] {"r"}, true).build();
        var one = org.apache.calcite.rel.logical.LogicalProject.create(oneBase, java.util.List.of(),
                java.util.List.of(), java.util.List.of());
        var actual = b.push(left).push(one)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.GREATER_THAN,
                        b.field(2, 0, 0), b.literal(7)))
                .build();
        var eb = builder();
        var expected = eb.push(left)
                .filter(eb.call(SqlStdOperatorTable.GREATER_THAN, eb.field(0), eb.literal(7)))
                .build();
        RuleChecks.rewrites(rule, actual, expected);
    }

    private static void rejectsMultipleRightRows(EliminateJoinNoColsRight rule) {
        var raw = RuleBuilder.create();
        var leftName = RuleChecks.table(raw, 1, false);
        var b = raw.transform(CalciteRuntime::configure);
        var left = b.scan(leftName).build();
        var twoRows = b.values(new String[] {"r"}, 1, 2).build();
        var plan = b.push(left).push(twoRows).join(JoinRelType.INNER, b.literal(true)).build();
        RuleChecks.doesNotApply(rule, plan);
    }

    private static RelBuilder builder() {
        return RuleBuilder.create().transform(CalciteRuntime::configure);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinNoColsRight test...");
        runTest();
    }
}
