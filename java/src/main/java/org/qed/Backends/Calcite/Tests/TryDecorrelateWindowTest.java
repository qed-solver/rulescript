package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.TryDecorrelateWindow;
import org.qed.RuleBuilder;

public class TryDecorrelateWindowTest {

    public static void runTest() {
        var rule = new TryDecorrelateWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateWindow rule) {
        var base = RuleBuilder.create();
        var left = RuleChecks.table(base, 1, true, false);
        var input = RuleChecks.table(base, 1, false, false);
        var b = RuleChecks.ruleBuilder(base);
        var perPart = b.scan(input)
                .aggregate(b.groupKey(), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(0))).build();
        var window = b.scan(input).push(perPart).join(JoinRelType.INNER, b.literal(true)).build();
        var before = b.scan(left).push(window)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.EQUALS, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        var cross = b.scan(left).scan(input).join(JoinRelType.INNER, b.literal(true)).build();
        var augPerPart = b.push(cross)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1))).build();
        var after = b.push(cross).push(augPerPart)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.EQUALS, b.field(2, 0, 0), b.field(2, 1, 0)))
                .filter(b.call(SqlStdOperatorTable.EQUALS, b.field(0), b.field(1)))
                .project(b.field(0), b.field(1), b.field(3))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var grouped = b.scan(input)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(0))).build();
        var groupedWindow = b.scan(input).push(grouped).join(JoinRelType.INNER, b.literal(true)).build();
        var nearMiss = b.scan(left).push(groupedWindow)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.EQUALS, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateWindow test...");
        runTest();
    }
}
