package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.CommuteConstInequality;
import org.qed.RuleBuilder;

public class CommuteConstInequalityTest {

    public static void runTest() {
        var rule = new CommuteConstInequality();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(CommuteConstInequality rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var before = builder.scan(l).scan(r)
                .join(JoinRelType.INNER, builder.literal(true))
                .filter(builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(0), builder.field(1)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.INNER, plain.literal(true))
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(1), plain.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var greater = builder.scan(l).scan(r)
                .join(JoinRelType.INNER, builder.literal(true))
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), builder.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, greater);
    }

    public static void main(String[] args) {
        System.out.println("Running CommuteConstInequality test...");
        runTest();
    }
}
