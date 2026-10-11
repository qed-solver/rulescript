package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ProjectSetOpTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectSetOpTransposeTest {

    public static void runTest() {
        var rule = new ProjectSetOpTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectSetOpTranspose rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(l).scan(r).union(true, 2).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)))
                .build();
        var after = b.scan(l).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1))).scan(r)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1))).union(true, 2).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).union(false, 2)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1))).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).intersect(true, 2)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1))).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).scan(l).union(true, 3)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectSetOpTranspose test...");
        runTest();
    }
}
