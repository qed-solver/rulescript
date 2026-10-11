package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.EliminateNot;
import org.qed.RuleBuilder;

public class EliminateNotTest {

    public static void runTest() {
        var rule = new EliminateNot();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        doubleNegation(rule);
    }

    private static void doubleNegation(EliminateNot rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var inner = builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT,
                        builder.call(SqlStdOperatorTable.NOT, inner)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(RuleChecks.gt(plain, plain.field(0), 5))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var single = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT, inner)).build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateNot test...");
        runTest();
    }
}
