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
        var plain = RuleChecks.ruleBuilder(builder);
        var before = plain.scan(table)
                .filter(plain.not(plain.and(
                        plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five),
                        plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(0), ten))))
                .build();
        var after = plain.scan(table)
                .filter(plain.or(
                        plain.not(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five)),
                        plain.not(plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(0), ten))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var single = plain.scan(table)
                .filter(plain.not(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five)))
                .build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running NegateAnd test...");
        runTest();
    }
}
