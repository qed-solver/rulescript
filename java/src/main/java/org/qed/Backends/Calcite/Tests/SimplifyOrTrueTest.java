package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyOrTrue;
import org.qed.RuleBuilder;

public class SimplifyOrTrueTest {
    public static void runTest() {
        var rule = new SimplifyOrTrue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        // Hand-built plan with a concrete comparison ensures matching is not limited to a symbolic predicate.
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var source = b.scan(table).build();
        var ref = b.getRexBuilder().makeInputRef(source.getRowType().getFieldList().get(0).getType(), 0);
        var comparison = b.call(SqlStdOperatorTable.GREATER_THAN, ref, b.literal(10));
        var condition = b.call(SqlStdOperatorTable.OR, comparison, b.literal(true));
        var input = LogicalFilter.create(source, condition);
        var expected = source;
        RuleChecks.rewrites(rule, input, expected);

        // A similar direct filter without TRUE must not match; bypass RelBuilder simplification.
        var nearMiss = LogicalFilter.create(source, comparison);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyOrTrue test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("Calcite rule test failed");
        }
    }
}
