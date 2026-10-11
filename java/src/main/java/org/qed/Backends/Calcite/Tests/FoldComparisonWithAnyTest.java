package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldComparisonWithAny;
import org.qed.RuleBuilder;

public class FoldComparisonWithAnyTest {

    public static void runTest() {
        var rule = new FoldComparisonWithAny();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldComparisonWithAny rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);

        b.scan(table);
        var before = b.filter(b.call(SqlStdOperatorTable.OR,
                        b.call(SqlStdOperatorTable.EQUALS, b.literal(true), b.literal(false)),
                        b.call(SqlStdOperatorTable.EQUALS, b.literal(true), b.literal(true))))
                .build();
        b.scan(table);
        var after = b.filter(b.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        b.scan(table);
        var single = b.filter(b.call(SqlStdOperatorTable.EQUALS, b.literal(true), b.literal(true))).build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldComparisonWithAny test...");
        runTest();
    }
}
