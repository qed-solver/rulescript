package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.CommuteVar;
import org.qed.RuleBuilder;

public class CommuteVarTest {

    public static void runTest() {
        var rule = new CommuteVar();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTable(rule);
    }

    private static void ordinaryTable(CommuteVar rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.EQUALS, five, builder.field(0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), plain.literal(5)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var variableOnRight = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), five))
                .build();
        RuleChecks.doesNotApply(rule, variableOnRight);
    }

    public static void main(String[] args) {
        System.out.println("Running CommuteVar test...");
        runTest();
    }
}
