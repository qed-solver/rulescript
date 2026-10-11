package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.RejectNullsLeftJoin;
import org.qed.RuleBuilder;

public class RejectNullsLeftJoinTest {

    public static void runTest() {
        var rule = new RejectNullsLeftJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RejectNullsLeftJoin rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(l).scan(r)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .filter(b.isNotNull(b.field(1))).build();
        var plain = RuleChecks.ruleBuilder(base);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.INNER, RuleChecks.eq(plain, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .filter(plain.isNotNull(plain.field(1))).build();
        RuleChecks.rewrites(rule, before, after);

        var leftNotNull = RuleChecks.ruleBuilder(base);
        var nearMiss = leftNotNull.scan(l).scan(r)
                .join(JoinRelType.LEFT, RuleChecks.eq(leftNotNull, leftNotNull.field(2, 0, 0), leftNotNull.field(2, 1, 0)))
                .filter(leftNotNull.isNotNull(leftNotNull.field(0))).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running RejectNullsLeftJoin test...");
        runTest();
    }
}
