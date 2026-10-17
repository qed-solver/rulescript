package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.MergeSelects;
import org.qed.RuleBuilder;

import java.util.List;

public class MergeSelectsTest {
    public static void runTest() {
        var rule = new MergeSelects();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MergeSelects rule) {
        var beforeBuilder = RuleBuilder.create();
        var beforeTable = RuleChecks.table(beforeBuilder, 1, false);
        var beforeScan = beforeBuilder.scan(beforeTable).build();
        var beforeRex = beforeBuilder.getRexBuilder();
        var beforeRef0 = beforeRex.makeInputRef(beforeScan, 0);
        var beforeRef1 = beforeRex.makeInputRef(beforeScan, 0);
        var beforeInner = beforeRex.makeCall(SqlStdOperatorTable.GREATER_THAN, beforeRef0, beforeRex.makeExactLiteral(java.math.BigDecimal.valueOf(10)));
        var beforeOuter = beforeRex.makeCall(SqlStdOperatorTable.EQUALS, beforeRef1, beforeRex.makeExactLiteral(java.math.BigDecimal.valueOf(7)));
        RelNode before = LogicalFilter.create(LogicalFilter.create(beforeScan, beforeInner), beforeOuter);

        var afterBuilder = RuleBuilder.create();
        var afterTable = RuleChecks.table(afterBuilder, 1, false);
        var afterScan = afterBuilder.scan(afterTable).build();
        var afterRex = afterBuilder.getRexBuilder();
        var afterRef0 = afterRex.makeInputRef(afterScan, 0);
        var afterRef1 = afterRex.makeInputRef(afterScan, 0);
        var afterInner = afterRex.makeCall(SqlStdOperatorTable.GREATER_THAN, afterRef0, afterRex.makeExactLiteral(java.math.BigDecimal.valueOf(10)));
        var afterOuter = afterRex.makeCall(SqlStdOperatorTable.EQUALS, afterRef1, afterRex.makeExactLiteral(java.math.BigDecimal.valueOf(7)));
        var combined = afterRex.makeCall(SqlStdOperatorTable.AND, List.of(afterInner, afterOuter));
        RelNode expected = LogicalFilter.create(afterScan, combined);
        RuleChecks.rewrites(rule, before, expected);

        var nearMissBuilder = RuleBuilder.create();
        var nearMissTable = RuleChecks.table(nearMissBuilder, 1, false);
        var nearMissScan = nearMissBuilder.scan(nearMissTable).build();
        var nearMissRex = nearMissBuilder.getRexBuilder();
        var nearMissRef = nearMissRex.makeInputRef(nearMissScan, 0);
        var nearMissCondition = nearMissRex.makeCall(SqlStdOperatorTable.GREATER_THAN, nearMissRef,
                nearMissRex.makeExactLiteral(java.math.BigDecimal.valueOf(10)));
        var nearMiss = LogicalFilter.create(nearMissScan, nearMissCondition);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running MergeSelects test...");
        runTest();
    }
}
