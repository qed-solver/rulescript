package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ProjectTableScan;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectTableScanTest {
    public static void runTest() {
        var rule = new ProjectTableScan();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectTableScan rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 3, false);
        var source = builder.scan(table);
        var plan = source.project(List.of(
                builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.field(2)),
                builder.field(2)), List.of(), true).build();

        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(table)
                .project(List.of(plain.field(0), plain.field(2)), List.of(), true)
                .project(List.of(plain.call(SqlStdOperatorTable.PLUS, plain.field(0), plain.field(1)),
                        plain.field(1)), List.of(), true)
                .build();
        RuleChecks.rewrites(rule, plan, expected);

        var nearMissBuilder = RuleBuilder.create();
        var nearMissTable = RuleChecks.table(nearMissBuilder, 3, false);
        var nearMiss = nearMissBuilder.scan(nearMissTable)
                .project(List.of(nearMissBuilder.call(SqlStdOperatorTable.PLUS,
                                nearMissBuilder.field(0), nearMissBuilder.field(1)),
                        nearMissBuilder.field(2)), List.of(), true)
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectTableScan test...");
        runTest();
    }
}
