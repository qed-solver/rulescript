package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyIntersectRight;
import org.qed.RuleBuilder;

public class SimplifyIntersectRightTest {

    public static void runTest() {
        var rule = new SimplifyIntersectRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SimplifyIntersectRight rule) {
        var base = RuleBuilder.create();
        var plain = RuleChecks.table(base, 1, false, false);
        var unique = RuleChecks.table(base, 1, true, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(plain).scan(unique).intersect(false, 2).build();
        var after = b.scan(unique).scan(plain)
                .join(JoinRelType.SEMI, b.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, b.field(2, 0, 0),
                        b.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(plain).scan(unique).intersect(true, 2).build());
        RuleChecks.doesNotApply(rule, b.scan(unique).scan(plain).intersect(false, 2).build());
        RuleChecks.doesNotApply(rule, b.scan(plain).scan(plain).intersect(false, 2).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyIntersectRight test...");
        runTest();
    }
}
