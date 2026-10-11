package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.MergeProjects;
import org.qed.RuleBuilder;

public class MergeProjectsTest {

    public static void runTest() {
        var rule = new MergeProjects();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MergeProjects rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var two = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t)
                .project(b.field(0), b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)))
                .project(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)))
                .build();
        b.scan(t);
        var after = b.project(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(two)
                .project(b.field(0), b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)))
                .project(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running MergeProjects test...");
        runTest();
    }
}
