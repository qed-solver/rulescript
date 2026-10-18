package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.rel.RelNode;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.ProjectMerge;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectMergeTest {
    public static void runTest() {
        var rule = new ProjectMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectMerge rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var beforeBuilder = RuleChecks.ruleBuilder(builder);
        beforeBuilder.scan(table);
        beforeBuilder.project(List.of(beforeBuilder.call(SqlStdOperatorTable.ABS, beforeBuilder.field(0))));
        beforeBuilder.project(List.of(beforeBuilder.call(SqlStdOperatorTable.ABS, beforeBuilder.field(0))));
        RelNode before = beforeBuilder.build();

        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        expectedBuilder.scan(table);
        var inner = expectedBuilder.call(SqlStdOperatorTable.ABS, expectedBuilder.field(0));
        expectedBuilder.project(List.of(expectedBuilder.call(SqlStdOperatorTable.ABS, inner)));
        RelNode expected = expectedBuilder.build();
        RuleChecks.rewrites(rule, before, expected);

        var wideBuilder = RuleBuilder.create();
        var wideTable = RuleChecks.table(wideBuilder, 2, false);
        var nearMissBuilder = RuleChecks.ruleBuilder(wideBuilder);
        nearMissBuilder.scan(wideTable);
        nearMissBuilder.project(List.of(nearMissBuilder.call(SqlStdOperatorTable.ABS, nearMissBuilder.field(0))));
        nearMissBuilder.project(List.of(nearMissBuilder.call(SqlStdOperatorTable.ABS, nearMissBuilder.field(0))));
        RuleChecks.doesNotApply(rule, nearMissBuilder.build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectMerge test...");
        runTest();
    }
}
