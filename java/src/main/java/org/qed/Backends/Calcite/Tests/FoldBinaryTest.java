package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldBinary;
import org.qed.RuleBuilder;

public class FoldBinaryTest {

    public static void runTest() {
        var rule = new FoldBinary();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldBinary rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var before = LogicalFilter.create(builder.scan(table).build(),
                builder.call(SqlStdOperatorTable.AND, builder.literal(true), builder.literal(false)));
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table).filter(plain.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        var single = builder.scan(table)
                .filter(RuleChecks.gt(builder, builder.field(0), 5)).build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldBinary test...");
        runTest();
    }
}
