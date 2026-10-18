package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneWithScanCols;
import org.qed.RuleBuilder;

public class PruneWithScanColsTest {
    public static void runTest() {
        var rule = new PruneWithScanCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneWithScanCols rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 3, false, false);
        var plan = builder.scan(table).project(builder.call(SqlStdOperatorTable.PLUS,
                builder.field(0), builder.field(1))).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(table).project(plain.field(0), plain.field(1))
                .project(plain.call(SqlStdOperatorTable.PLUS, plain.field(0), plain.field(1))).build();
        RuleChecks.rewrites(rule, plan, expected);

        var fourBuilder = RuleBuilder.create();
        var four = RuleChecks.table(fourBuilder, 4, false, false);
        var fourColumnPlan = fourBuilder.scan(four).project(fourBuilder.call(SqlStdOperatorTable.PLUS,
                fourBuilder.field(0), fourBuilder.field(1))).build();
        RuleChecks.doesNotApply(rule, fourColumnPlan);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneWithScanCols test...");
        runTest();
    }
}
