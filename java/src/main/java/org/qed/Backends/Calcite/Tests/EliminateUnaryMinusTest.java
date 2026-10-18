package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateUnaryMinus;

import java.util.List;

public class EliminateUnaryMinusTest {
    public static void runTest() {
        var rule = new EliminateUnaryMinus();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var ruleBuilder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        builder.scan(table);
        var x = builder.field(0);
        var nested = builder.call(SqlStdOperatorTable.UNARY_MINUS,
                builder.call(SqlStdOperatorTable.UNARY_MINUS, x));
        RelNode positive = LogicalProject.create(builder.peek(), List.of(), List.of(nested), List.of("x"));
        var expectedBuilder = RuleChecks.ruleBuilder(ruleBuilder);
        RelNode expected = expectedBuilder.scan(table).build();
        RuleChecks.rewrites(rule, positive, expected);

        var noMatchBuilder = RuleChecks.ruleBuilder(ruleBuilder);
        noMatchBuilder.scan(table);
        var singleMinus = noMatchBuilder.call(SqlStdOperatorTable.UNARY_MINUS, noMatchBuilder.field(0));
        RelNode noMatch = LogicalProject.create(noMatchBuilder.peek(), List.of(), List.of(singleMinus), List.of("x"));
        RuleChecks.doesNotApply(rule, noMatch);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateUnaryMinus test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("EliminateUnaryMinus test failures: " + CalciteTester.failures());
        }
    }
}
