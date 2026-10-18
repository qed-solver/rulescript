package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.RejectNullsProject;
import org.qed.RuleBuilder;

import java.util.List;

public class RejectNullsProjectTest {
    public static void main(String[] args) {
        System.out.println("Running RejectNullsProject test...");
        runTest();
    }

    public static void runTest() {
        var rule = new RejectNullsProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RejectNullsProject rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        var source = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var input = rex.makeInputRef(source, 0);
        var negated = rex.makeCall(SqlStdOperatorTable.UNARY_MINUS, input);
        RelNode project = org.apache.calcite.rel.logical.LogicalProject.create(source,
                List.of(), List.of(negated, input), List.of("c", "a"));
        var projected = rex.makeInputRef(project, 0);
        RelNode plan = LogicalFilter.create(project,
                rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, projected));

        var plain = RuleChecks.ruleBuilder(builder);
        var expectedSource = plain.scan(table).build();
        var expectedRex = plain.getRexBuilder();
        var expectedInput = expectedRex.makeInputRef(expectedSource, 0);
        RelNode filtered = LogicalFilter.create(expectedSource,
                expectedRex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, expectedInput));
        var expectedNegated = expectedRex.makeCall(SqlStdOperatorTable.UNARY_MINUS,
                expectedRex.makeInputRef(filtered, 0));
        RelNode expectedProject = org.apache.calcite.rel.logical.LogicalProject.create(filtered,
                List.of(), List.of(expectedNegated, expectedRex.makeInputRef(filtered, 0)), List.of("c", "a"));
        var expectedProjected = expectedRex.makeInputRef(expectedProject, 0);
        RelNode expected = LogicalFilter.create(expectedProject,
                expectedRex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, expectedProjected));
        RuleChecks.rewrites(rule, plan, expected);

        RelNode nearMissProject = org.apache.calcite.rel.logical.LogicalProject.create(source,
                List.of(), List.of(rex.makeCall(SqlStdOperatorTable.UNARY_PLUS, input), input), List.of("c", "a"));
        var nearMissProjected = rex.makeInputRef(nearMissProject, 0);
        RelNode nearMiss = LogicalFilter.create(nearMissProject,
                rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, nearMissProjected));
        RuleChecks.doesNotApply(rule, nearMiss);
    }
}
