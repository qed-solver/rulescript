package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
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
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 3, false);
        var two = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(2)), b.field(2)).build();
        var after = b.scan(t).project(b.field(0), b.field(2))
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)), b.field(1)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule,
                b.scan(t).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(2)), b.field(1)).build());
        RuleChecks.doesNotApply(rule,
                b.scan(two).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)), b.field(1)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectTableScan test...");
        runTest();
    }
}
