package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.SimplifyBinaryComparison;
import org.qed.RuleBuilder;

public class SimplifyBinaryComparisonTest {
    public static void runTest() {
        var rule = new SimplifyBinaryComparison();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        rewritesHandBuiltSelfEquality();
        rejectsNullableColumn();
    }

    private static void rewritesHandBuiltSelfEquality() {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, false);
        var builder = RuleChecks.ruleBuilder(qed);
        RelNode plan = builder.scan(table)
                .filter(RuleChecks.eq(builder, builder.field(0), builder.field(0)))
                .build();
        var expected = ((LogicalFilter) plan).getInput();
        RuleChecks.rewrites(new SimplifyBinaryComparison(), plan, expected);
    }

    private static void rejectsNullableColumn() {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, true);
        var builder = RuleChecks.ruleBuilder(qed);
        var input = builder.scan(table).build();
        var ref = builder.getRexBuilder().makeInputRef(input, 0);
        var condition = builder.call(SqlStdOperatorTable.EQUALS, ref, ref);
        RelNode plan = builder.push(input).filter(condition).build();
        RuleChecks.doesNotApply(new SimplifyBinaryComparison(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyBinaryComparison test...");
        runTest();
    }
}
