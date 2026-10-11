package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyLeftJoin;
import org.qed.RuleBuilder;

public class SimplifyLeftJoinTest {

    public static void runTest() {
        var rule = new SimplifyLeftJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        selfJoin(rule);
    }

    private static void selfJoin(SimplifyLeftJoin rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var before = builder.scan(table).scan(table)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table).scan(table)
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nullable = RuleChecks.table(builder, 1, false);
        var nullableLeft = builder.scan(nullable).scan(nullable)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, nullableLeft);

        var innerJoin = builder.scan(table).scan(table)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, innerJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyLeftJoin test...");
        runTest();
    }
}
