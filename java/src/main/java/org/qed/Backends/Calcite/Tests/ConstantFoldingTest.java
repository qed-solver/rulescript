package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ConstantFolding;
import org.qed.RuleBuilder;

public class ConstantFoldingTest {
    public static void runTest() {
        var rule = new ConstantFolding();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsHandBuiltNearMiss();
    }

    private static void rewritesHandBuiltPlan() {
        var builder = RuleBuilder.create();
        var relBuilder = builder.transform(org.qed.Backends.Calcite.CalciteRuntime::configure);
        var input = RuleChecks.table(builder, 1, false);
        RelNode source = relBuilder.scan(input).build();
        var rexBuilder = relBuilder.getRexBuilder();
        var condition = rexBuilder.makeCall(SqlStdOperatorTable.AND,
                rexBuilder.makeLiteral(true), rexBuilder.makeLiteral(false));
        RelNode before = LogicalFilter.create(source, condition);

        var expectedBuilder = RuleBuilder.create();
        var expectedRelBuilder = expectedBuilder.transform(org.qed.Backends.Calcite.CalciteRuntime::configure);
        var expectedInput = RuleChecks.table(expectedBuilder, 1, false);
        RelNode expectedSource = expectedRelBuilder.scan(expectedInput).build();
        RexBuilder expectedRexBuilder = expectedRelBuilder.getRexBuilder();
        RelNode after = LogicalFilter.create(expectedSource, expectedRexBuilder.makeLiteral(false));
        rewrites(new ConstantFolding(), before, after);
    }

    private static void rejectsHandBuiltNearMiss() {
        var builder = RuleBuilder.create();
        var relBuilder = builder.transform(org.qed.Backends.Calcite.CalciteRuntime::configure);
        var input = RuleChecks.table(builder, 1, false);
        RelNode source = relBuilder.scan(input).build();
        var rexBuilder = relBuilder.getRexBuilder();
        var condition = rexBuilder.makeCall(SqlStdOperatorTable.AND,
                rexBuilder.makeLiteral(false), rexBuilder.makeLiteral(true));
        RuleChecks.doesNotApply(new ConstantFolding(), LogicalFilter.create(source, condition));
    }

    private static void rewrites(org.qed.RRule rule, RelNode before, RelNode after) {
        RuleChecks.rewrites(rule, before, after);
    }

    public static void main(String[] args) {
        System.out.println("Running ConstantFolding test...");
        runTest();
    }
}
