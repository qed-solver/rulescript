package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.InlineProjectInProject;
import org.qed.RuleBuilder;

public class InlineProjectInProjectTest {

    public static void runTest() {
        var rule = new InlineProjectInProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(InlineProjectInProject rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var two = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)))
                .project(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)), b.field(0))
                .build();
        var after = b.scan(t).project(
                b.call(SqlStdOperatorTable.MULTIPLY,
                        b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)), b.literal(2)),
                b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1))).build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = b.scan(two)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)), b.field(1))
                .project(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)), b.field(0))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running InlineProjectInProject test...");
        runTest();
    }
}
