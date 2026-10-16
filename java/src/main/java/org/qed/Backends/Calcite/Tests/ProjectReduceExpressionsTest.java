package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ProjectReduceExpressions;
import org.qed.RuleBuilder;

public class ProjectReduceExpressionsTest {

    public static void runTest() {
        var rule = new ProjectReduceExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectReduceExpressions rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t)
                .project(b.call(SqlStdOperatorTable.AND, b.literal(false), RuleChecks.gt(b, b.field(0), 0))).build();
        var after = b.scan(t).project(b.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t)
                .project(b.call(SqlStdOperatorTable.AND, b.literal(true), RuleChecks.gt(b, b.field(0), 0))).build());
        RuleChecks.doesNotApply(rule, b.scan(t).project(RuleChecks.gt(b, b.field(0), 0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectReduceExpressions test...");
        runTest();
    }
}
