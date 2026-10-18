package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FilterToCalc;

import java.util.List;

public final class FilterToCalcTest {
    public static void runTest() {
        var rule = new FilterToCalc();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPositive(rule);
        handBuiltNoMatch(rule);
    }

    private static void handBuiltPositive(FilterToCalc rule) {
        var input = FaithfulPlans.of(rule.before());
        var rex = input.getCluster().getRexBuilder();
        var condition = rex.makeCall(SqlStdOperatorTable.GREATER_THAN,
                rex.makeInputRef(input, 0), rex.makeExactLiteral(java.math.BigDecimal.valueOf(7)));
        RelNode filter = LogicalFilter.create(input, condition);
        RuleChecks.rewrites(rule, filter, filter);
    }

    private static void handBuiltNoMatch(FilterToCalc rule) {
        var input = FaithfulPlans.of(rule.before());
        var rex = input.getCluster().getRexBuilder();
        var scan = input.getInput(0);
        var project = LogicalProject.create(scan, List.of(), List.of(rex.makeInputRef(scan, 0)),
                (java.util.List<String>) null);
        RuleChecks.doesNotApply(rule, project);
    }

    public static void main(String[] args) {
        System.out.println("Running FilterToCalc test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("FilterToCalc test had " + CalciteTester.failures() + " failure(s)");
        }
    }
}
