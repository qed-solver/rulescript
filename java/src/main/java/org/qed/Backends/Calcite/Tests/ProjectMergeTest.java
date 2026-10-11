package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
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
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var two = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)))
                .project(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2))).build();
        b.scan(t);
        var after = b.project(b.call(SqlStdOperatorTable.MULTIPLY,
                b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)), b.literal(2))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(two).project(b.field(0), b.field(1))
                .project(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectMerge test...");
        runTest();
    }
}
