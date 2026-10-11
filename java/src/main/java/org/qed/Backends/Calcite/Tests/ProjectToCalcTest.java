package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ProjectToCalc;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectToCalcTest {

    public static void runTest() {
        var rule = new ProjectToCalc();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectToCalc rule) {
        var base = RuleBuilder.create();
        var s = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var project = b.scan(s).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)),
                b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.field(1))).build();
        RuleChecks.rewrites(rule, project, b.scan(s).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)),
                b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.field(1))).build());

        RuleChecks.doesNotApply(rule,
                b.scan(s).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectToCalc test...");
        runTest();
    }
}
