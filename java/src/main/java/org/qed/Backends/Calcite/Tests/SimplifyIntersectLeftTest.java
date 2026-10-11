package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyIntersectLeft;
import org.qed.RuleBuilder;

public class SimplifyIntersectLeftTest {

    public static void runTest() {
        var rule = new SimplifyIntersectLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SimplifyIntersectLeft rule) {
        var base = RuleBuilder.create();
        var unique = RuleChecks.table(base, 1, true, false);
        var plain = RuleChecks.table(base, 1, false, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(unique).scan(plain).intersect(false, 2).build();
        var after = b.scan(unique).scan(plain)
                .join(JoinRelType.SEMI, b.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, b.field(2, 0, 0),
                        b.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(plain).scan(unique).intersect(false, 2).build());
        RuleChecks.doesNotApply(rule, b.scan(unique).scan(plain).intersect(true, 2).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyIntersectLeft test...");
        runTest();
    }
}
