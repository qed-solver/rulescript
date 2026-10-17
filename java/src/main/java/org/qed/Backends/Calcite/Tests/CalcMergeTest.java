package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.CalcMerge;
import org.qed.RuleBuilder;

import java.util.List;

public class CalcMergeTest {
    public static void runTest() {
        var rule = new CalcMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        var builder = RuleBuilder.create();
        var name = RuleChecks.table(builder, 2, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var source = b.scan(name).build();
        var rex = b.getRexBuilder();
        var x = rex.makeInputRef(source, 0);
        var y = rex.makeInputRef(source, 1);
        var bottomCondition = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, x, y);
        var bottomX = rex.makeCall(SqlStdOperatorTable.PLUS, x, rex.makeExactLiteral(java.math.BigDecimal.TEN));
        var bottomY = rex.makeCall(SqlStdOperatorTable.PLUS, y, rex.makeExactLiteral(java.math.BigDecimal.valueOf(20)));
        RelNode bottomFilter = LogicalFilter.create(source, bottomCondition);
        RelNode bottomProject = LogicalProject.create(bottomFilter, List.of(), List.of(bottomX, bottomY), List.of("a", "b"));
        var c0 = rex.makeInputRef(bottomProject, 0);
        var c1 = rex.makeInputRef(bottomProject, 1);
        var topCondition = rex.makeCall(SqlStdOperatorTable.LESS_THAN, c0, c1);
        RelNode topFilter = LogicalFilter.create(bottomProject, topCondition);
        RelNode before = LogicalProject.create(topFilter, List.of(), List.of(c1, c0), List.of("out0", "out1"));

        var expectedCondition = rex.makeCall(SqlStdOperatorTable.AND, bottomCondition,
                rex.makeCall(SqlStdOperatorTable.LESS_THAN, bottomX, bottomY));
        RelNode expectedFilter = LogicalFilter.create(source, expectedCondition);
        RelNode expected = LogicalProject.create(expectedFilter, List.of(), List.of(bottomY, bottomX), List.of("out0", "out1"));
        RuleChecks.rewrites(rule, before, expected);

        var widerBuilder = RuleBuilder.create();
        var widerName = RuleChecks.table(widerBuilder, 3, false, false);
        var w = RuleChecks.ruleBuilder(widerBuilder);
        var widerSource = w.scan(widerName).build();
        var wr = w.getRexBuilder();
        var wx = wr.makeInputRef(widerSource, 0);
        var wy = wr.makeInputRef(widerSource, 1);
        var wz = wr.makeInputRef(widerSource, 2);
        var widerBottom = LogicalProject.create(
                LogicalFilter.create(widerSource, wr.makeCall(SqlStdOperatorTable.GREATER_THAN, wx, wy)),
                List.of(), List.of(wx, wy), List.of("a", "b"));
        var w0 = wr.makeInputRef(widerBottom, 0);
        var w1 = wr.makeInputRef(widerBottom, 1);
        var widerTop = LogicalProject.create(
                LogicalFilter.create(widerBottom, wr.makeCall(SqlStdOperatorTable.LESS_THAN, w0, w1)),
                List.of(), List.of(w1, w0), List.of("out0", "out1"));
        RuleChecks.doesNotApply(rule, widerTop);
    }

    public static void main(String[] args) {
        System.out.println("Running CalcMerge test...");
        runTest();
    }
}
