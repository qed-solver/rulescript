package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.CommuteConst;
import org.qed.RuleBuilder;

public class CommuteConstTest {

    public static void runTest() {
        var rule = new CommuteConst();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(CommuteConst rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var before = builder.scan(l).scan(r)
                .join(JoinRelType.INNER, builder.literal(true))
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.INNER, plain.literal(true))
                .filter(plain.call(SqlStdOperatorTable.EQUALS, plain.field(1), plain.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var leftJoin = builder.scan(l).scan(r)
                .join(JoinRelType.LEFT, builder.literal(true))
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, leftJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running CommuteConst test...");
        runTest();
    }
}
