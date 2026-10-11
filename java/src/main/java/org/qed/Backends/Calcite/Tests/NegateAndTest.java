package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NegateAnd;
import org.qed.RuleBuilder;

public class NegateAndTest {

    public static void runTest() {
        var rule = new NegateAnd();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        negatedConjunction(rule);
    }

    private static void negatedConjunction(NegateAnd rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var ten = builder.literal(10);
        var greater = builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five);
        var less = builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(0), ten);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT,
                        builder.call(SqlStdOperatorTable.AND, greater, less)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.OR,
                        plain.call(SqlStdOperatorTable.NOT,
                                plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five)),
                        plain.call(SqlStdOperatorTable.NOT,
                                plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(0), ten))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var single = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT, greater)).build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running NegateAnd test...");
        runTest();
    }
}
