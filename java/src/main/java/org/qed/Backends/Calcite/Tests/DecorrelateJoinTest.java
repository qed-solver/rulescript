package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.DecorrelateJoin;
import org.qed.RuleBuilder;

public class DecorrelateJoinTest {

    public static void runTest() {
        var rule = new DecorrelateJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(DecorrelateJoin rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var before = RuleChecks.correlate(b, b.scan(left).build(), b.scan(right).build(), JoinRelType.INNER,
                args -> b.call(SqlStdOperatorTable.EQUALS, args[0], args[1]));
        var after = b.scan(left).scan(right)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, RuleChecks.correlate(b, b.scan(left).build(), b.scan(right).build(),
                JoinRelType.LEFT, args -> b.call(SqlStdOperatorTable.EQUALS, args[0], args[1])));
        RuleChecks.doesNotApply(rule, b.scan(left).scan(right)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running DecorrelateJoin test...");
        runTest();
    }
}
