package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NormalizeNestedAnds;
import org.qed.RuleBuilder;

public class NormalizeNestedAndsTest {

    public static void runTest() {
        var rule = new NormalizeNestedAnds();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NormalizeNestedAnds rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var ten = builder.literal(10);
        var seven = builder.literal(7);
        var p = builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five);
        var q = builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(0), ten);
        var r = builder.call(SqlStdOperatorTable.NOT_EQUALS, builder.field(0), seven);
        var before = builder.scan(table)
                .filter(builder.and(p, builder.and(q, r)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.and(plain.and(p, q), r))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var twoConjuncts = builder.scan(table)
                .filter(builder.and(p, q))
                .build();
        RuleChecks.doesNotApply(rule, twoConjuncts);
    }

    public static void main(String[] args) {
        System.out.println("Running NormalizeNestedAnds test...");
        runTest();
    }
}
