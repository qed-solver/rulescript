package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRule;
import org.qed.RRuleInstances.FoldNonNullIsNotNull;
import org.qed.RuleBuilder;

public final class FoldNonNullIsNotNullTest {
    public static void runTest() {
        RRule rule = new FoldNonNullIsNotNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        relBuilder.scan(table);
        RelNode scan = relBuilder.build();
        RexBuilder rexBuilder = relBuilder.getRexBuilder();
        var condition = rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                rexBuilder.makeInputRef(scan.getRowType().getFieldList().get(0).getType(), 0));
        RelNode before = LogicalFilter.create(scan, condition);
        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false, false);
        var expectedRelBuilder = RuleChecks.ruleBuilder(expectedBuilder);
        expectedRelBuilder.scan(expectedTable);
        RelNode expected = expectedRelBuilder.build();
        RuleChecks.rewrites(rule, before, expected);

        var nullableBuilder = RuleBuilder.create();
        var nullableTable = RuleChecks.table(nullableBuilder, 1, false, true);
        var nullableRelBuilder = RuleChecks.ruleBuilder(nullableBuilder);
        nullableRelBuilder.scan(nullableTable);
        RelNode nullableScan = nullableRelBuilder.build();
        var nullableRexBuilder = nullableRelBuilder.getRexBuilder();
        RelNode nullableInput = LogicalFilter.create(nullableScan, nullableRexBuilder.makeCall(
                SqlStdOperatorTable.IS_NOT_NULL,
                nullableRexBuilder.makeInputRef(nullableScan.getRowType().getFieldList().get(0).getType(), 0)));
        RuleChecks.doesNotApply(rule, nullableInput);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNonNullIsNotNull test...");
        runTest();
    }
}
