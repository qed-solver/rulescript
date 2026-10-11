package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldComparison;
import org.qed.RuleBuilder;

public class FoldComparisonTest {

    public static void runTest() {
        var rule = new FoldComparison();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        trueEqualsFalse(rule);
    }

    private static void trueEqualsFalse(FoldComparison rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var before = LogicalFilter.create(plain.scan(table).build(),
                plain.call(SqlStdOperatorTable.EQUALS, plain.literal(true), plain.literal(false)));
        var after = plain.scan(table).filter(plain.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        var five = builder.literal(5);
        var selective = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five)).build();
        RuleChecks.doesNotApply(rule, selective);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldComparison test...");
        runTest();
    }
}
