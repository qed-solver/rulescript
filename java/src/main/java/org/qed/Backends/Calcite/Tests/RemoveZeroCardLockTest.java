package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RelRN;
import org.qed.RRuleInstances.RemoveZeroCardLock;
import org.qed.RuleBuilder;

public class RemoveZeroCardLockTest {
    public static void runTest() {
        var rule = new RemoveZeroCardLock();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
        handBuiltPlans(rule);
        rejectsMissingFilter(rule);
    }

    private static void ordinaryTables(RemoveZeroCardLock rule) {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var builder = ruleBuilder.transform(CalciteRuntime::configure);
        builder.scan(table);
        var input = builder.build();
        var condition = input.getCluster().getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                input.getCluster().getRexBuilder().makeInputRef(input, 0));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(input, condition));
    }

    private static void handBuiltPlans(RemoveZeroCardLock rule) {
        var source = RelRN.scan("HandBuilt", "HandBuilt_Type");
        var empty = (LogicalValues) source.empty().semantics();
        var rexBuilder = empty.getCluster().getRexBuilder();
        var condition = rexBuilder.makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                rexBuilder.makeInputRef(empty, 0));
        RelNode before = LogicalFilter.create(empty, condition);
        RelNode after = LogicalValues.create(empty.getCluster(), empty.getRowType(),
                com.google.common.collect.ImmutableList.of());
        RuleChecks.rewrites(rule, before, after);
    }

    private static void rejectsMissingFilter(RemoveZeroCardLock rule) {
        var empty = (LogicalValues) RelRN.scan("NoFilter", "NoFilter_Type").empty().semantics();
        RuleChecks.doesNotApply(rule, empty);
    }

    public static void main(String[] args) {
        System.out.println("Running RemoveZeroCardLock test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("RemoveZeroCardLock test had " + CalciteTester.failures() + " failure(s)");
        }
    }
}
