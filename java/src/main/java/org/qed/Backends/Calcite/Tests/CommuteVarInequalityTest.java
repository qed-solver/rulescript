package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.CommuteVarInequality;
import org.qed.RuleBuilder;

public class CommuteVarInequalityTest {

    public static void runTest() {
        var rule = new CommuteVarInequality();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(CommuteVarInequality rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var before = builder.scan(l).scan(r)
                .join(JoinRelType.INNER, builder.literal(true))
                .filter(builder.call(SqlStdOperatorTable.LESS_THAN_OR_EQUAL, builder.field(0), builder.field(1)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).scan(r)
                .join(JoinRelType.INNER, plain.literal(true))
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN_OR_EQUAL, plain.field(1), plain.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var greater = builder.scan(l).scan(r)
                .join(JoinRelType.INNER, builder.literal(true))
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), builder.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, greater);
    }

    public static void main(String[] args) {
        System.out.println("Running CommuteVarInequality test...");
        runTest();
    }
}
