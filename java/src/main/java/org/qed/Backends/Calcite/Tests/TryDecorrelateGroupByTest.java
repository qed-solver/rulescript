package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.TryDecorrelateGroupBy;
import org.qed.RuleBuilder;

public class TryDecorrelateGroupByTest {

    public static void runTest() {
        var rule = new TryDecorrelateGroupBy();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateGroupBy rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, true, false);
        var plain = RuleChecks.table(base, 1, false, false);
        var r = RuleChecks.table(base, 2, false, false);
        var b = RuleChecks.ruleBuilder(base);
        var grouped = b.scan(r).aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1))).build();
        var before = b.scan(l).push(grouped).join(JoinRelType.INNER, b.literal(true))
                .filter(RuleChecks.gt(b, b.field(2), 0)).build();
        var after = b.scan(l).scan(r).join(JoinRelType.INNER, b.literal(true))
                .aggregate(b.groupKey(0, 1), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(2)))
                .filter(RuleChecks.gt(b, b.field(2), 0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(plain).push(grouped).join(JoinRelType.INNER, b.literal(true))
                .filter(RuleChecks.gt(b, b.field(2), 0)).build());
        RuleChecks.doesNotApply(rule, b.scan(l).push(grouped).join(JoinRelType.LEFT, b.literal(true))
                .filter(RuleChecks.gt(b, b.field(2), 0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateGroupBy test...");
        runTest();
    }
}
