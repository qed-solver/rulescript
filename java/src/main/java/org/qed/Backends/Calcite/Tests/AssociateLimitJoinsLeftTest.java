package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.AssociateLimitJoinsLeft;
import org.qed.RuleBuilder;

public class AssociateLimitJoinsLeftTest {

    public static void runTest() {
        var rule = new AssociateLimitJoinsLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AssociateLimitJoinsLeft rule) {
        var builder = RuleBuilder.create();
        var a = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.table(builder, 1, false);
        var c = RuleChecks.table(builder, 1, false);
        var before = builder.scan(a).scan(b)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .scan(c)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(a).scan(c)
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .scan(b)
                .join(JoinRelType.LEFT, plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .project(plain.field(1, 0, 0), plain.field(1, 0, 2), plain.field(1, 0, 1))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(a).scan(b)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .scan(c)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 1), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running AssociateLimitJoinsLeft test...");
        runTest();
    }
}
