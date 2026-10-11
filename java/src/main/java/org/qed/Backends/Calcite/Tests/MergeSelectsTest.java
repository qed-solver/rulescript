package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.MergeSelects;
import org.qed.RuleBuilder;

public class MergeSelectsTest {

    public static void runTest() {
        var rule = new MergeSelects();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MergeSelects rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var five = builder.literal(5);
        var three = builder.literal(3);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five))
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(1), three))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table).filter(plain.and(
                        plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five),
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(1), three)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var single = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five)).build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running MergeSelects test...");
        runTest();
    }
}
