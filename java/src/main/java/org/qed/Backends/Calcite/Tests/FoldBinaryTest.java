package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldBinary;
import org.qed.RuleBuilder;

public final class FoldBinaryTest {
    public static void runTest() {
        var rule = new FoldBinary();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var relBuilder = builder.transform(CalciteRuntime::configure);
        RelNode source = relBuilder.scan(table).build();
        var rexBuilder = relBuilder.getRexBuilder();
        var condition = rexBuilder.makeCall(SqlStdOperatorTable.AND,
                rexBuilder.makeLiteral(true), rexBuilder.makeLiteral(false));
        RelNode before = LogicalFilter.create(source, condition);

        var expectedRuleBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedRuleBuilder, 1, false);
        var expectedBuilder = expectedRuleBuilder.transform(CalciteRuntime::configure);
        var expectedSource = expectedBuilder.scan(expectedTable).build();
        RelNode expected = LogicalFilter.create(expectedSource,
                expectedBuilder.getRexBuilder().makeLiteral(false));
        RuleChecks.rewrites(rule, before, expected);

        var nearRuleBuilder = RuleBuilder.create();
        var nearTable = RuleChecks.table(nearRuleBuilder, 1, false);
        var nearBuilder = nearRuleBuilder.transform(CalciteRuntime::configure);
        var nearSource = nearBuilder.scan(nearTable).build();
        var nearCondition = nearBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.OR,
                nearBuilder.getRexBuilder().makeLiteral(true), nearBuilder.getRexBuilder().makeLiteral(false));
        RelNode nearMiss = LogicalFilter.create(nearSource, nearCondition);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldBinary test...");
        runTest();
    }
}
