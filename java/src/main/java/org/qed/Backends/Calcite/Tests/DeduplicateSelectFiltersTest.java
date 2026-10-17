package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.DeduplicateSelectFilters;
import org.qed.RuleBuilder;

public class DeduplicateSelectFiltersTest {
    public static void runTest() {
        var rule = new DeduplicateSelectFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        RelNode scan = relBuilder.scan(table).build();
        var input = relBuilder.getRexBuilder().makeInputRef(scan, 0);
        var p = relBuilder.call(SqlStdOperatorTable.LESS_THAN, input, relBuilder.literal(7));
        var q = relBuilder.call(SqlStdOperatorTable.GREATER_THAN, input, relBuilder.literal(2));
        var r = relBuilder.call(SqlStdOperatorTable.EQUALS, input, relBuilder.literal(5));

        RelNode concreteBefore = LogicalFilter.create(
                LogicalFilter.create(LogicalFilter.create(scan, p), q), p);
        RelNode concreteAfter = LogicalFilter.create(LogicalFilter.create(scan, p), q);
        RuleChecks.rewrites(rule, concreteBefore, concreteAfter);

        RelNode nearMiss = LogicalFilter.create(
                LogicalFilter.create(LogicalFilter.create(scan, p), q), r);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running DeduplicateSelectFilters test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
