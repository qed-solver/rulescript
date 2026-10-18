package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ProjectReduceExpressions;

import java.util.List;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public final class ProjectReduceExpressionsTest {
    public static void runTest() {
        System.out.println("Running RULE test...");
        var rule = new ProjectReduceExpressions();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);
        ordinaryTables();

        var qedBuilder = org.qed.RuleBuilder.create();
        var table = table(qedBuilder, 1, false);
        var builder = ruleBuilder(qedBuilder);
        var input = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        RexNode predicate = rex.makeCall(SqlStdOperatorTable.GREATER_THAN,
                rex.makeInputRef(input, 0), rex.makeExactLiteral(java.math.BigDecimal.ZERO));
        var before = LogicalProject.create(input, List.of(),
                List.of(rex.makeCall(SqlStdOperatorTable.AND, rex.makeLiteral(false), predicate)), (List<String>) null);
        var expected = LogicalProject.create(input, List.of(), List.of(rex.makeLiteral(false)), (List<String>) null);
        rewrites(rule, before, expected);

        var nearMiss = LogicalProject.create(input, List.of(),
                List.of(rex.makeCall(SqlStdOperatorTable.AND, rex.makeLiteral(true), predicate)), (List<String>) null);
        doesNotApply(rule, nearMiss);
    }

    private static void ordinaryTables() {
        var builder = org.qed.RuleBuilder.create();
        var table = table(builder, 1, false);
        var scan = FaithfulPlans.of(new ProjectReduceExpressions().before());
        var ordinary = ruleBuilder(builder).scan(table).build();
        if (scan.getRowType().getFieldCount() != 1 || ordinary.getRowType().getFieldCount() != 1) {
            throw new AssertionError("ordinary one-column table setup failed");
        }
        System.out.println("succeeded (ordinary one-column table)");
    }

    public static void main(String[] args) {
        runTest();
    }
}
