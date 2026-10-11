package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ProjectRemove;
import org.qed.RuleBuilder;

public class ProjectRemoveTest {

    public static void runTest() {
        var rule = new ProjectRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectRemove rule) {
        var builder = RuleBuilder.create();
        var one = RuleChecks.table(builder, 1, false);
        var two = RuleChecks.table(builder, 2, false);
        var identity = builder.scan(one).project(java.util.List.of(builder.field(0)), java.util.List.of(), true).build();
        RuleChecks.rewrites(rule, identity, builder.scan(one).build());
        var wide = builder.scan(two).project(java.util.List.of(builder.field(0), builder.field(1)), java.util.List.of(),
                true).build();
        RuleChecks.doesNotApply(rule, wide);
        var computed = builder.scan(one).project(java.util.List.of(builder.call(SqlStdOperatorTable.PLUS,
                builder.field(0), builder.literal(1))), java.util.List.of(), true).build();
        RuleChecks.doesNotApply(rule, computed);
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectRemove test...");
        runTest();
    }
}
