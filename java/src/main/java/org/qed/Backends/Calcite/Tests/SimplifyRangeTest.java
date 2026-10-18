package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.SimplifyRange;
import org.qed.RuleBuilder;

public class SimplifyRangeTest {
    public static void runTest() {
        RuleChecks.rewritesPattern(new SimplifyRange());
        RuleChecks.rewritesConcretePredicates(new SimplifyRange());
        RuleChecks.rejectsNearMisses(new SimplifyRange());
        rewritesHandBuiltFilter();
    }

    private static void rewritesHandBuiltFilter() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var input = b.scan(table).build();
        var ref = b.getRexBuilder().makeInputRef(input, 0);
        var predicate = b.call(SqlStdOperatorTable.GREATER_THAN, ref, b.literal(7));
        var condition = b.call(SqlStdOperatorTable.AND, predicate, b.literal(true));
        var before = LogicalFilter.create(input, condition);

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false, false);
        var eb = RuleChecks.ruleBuilder(expectedBuilder);
        var expectedInput = eb.scan(expectedTable).build();
        var expectedRef = eb.getRexBuilder().makeInputRef(expectedInput, 0);
        var expectedPredicate = eb.call(SqlStdOperatorTable.GREATER_THAN, expectedRef, eb.literal(7));
        var expected = LogicalFilter.create(expectedInput, expectedPredicate);
        RuleChecks.rewrites(new SimplifyRange(), before, expected);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyRange test...");
        runTest();
    }
}
