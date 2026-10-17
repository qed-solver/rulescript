package org.qed.Backends.Calcite.Tests;

import com.google.common.collect.ImmutableSet;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.NormalizeSelectAnyFilter;
import org.qed.RuleBuilder;

import java.util.List;

public class NormalizeSelectAnyFilterTest {
    public static void runTest() {
        var rule = new NormalizeSelectAnyFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NormalizeSelectAnyFilter rule) {
        var beforeBase = RuleBuilder.create();
        var outerName = RuleChecks.table(beforeBase, 1, false);
        var innerName = RuleChecks.table(beforeBase, 1, false);
        RelBuilder beforeBuilder = RuleChecks.ruleBuilder(beforeBase);
        RelNode outer = beforeBuilder.scan(outerName).build();
        RelNode inner = beforeBuilder.scan(innerName).build();
        var beforeRef = beforeBuilder.getRexBuilder().makeInputRef(outer, 0);
        RelNode before = LogicalFilter.create(outer,
                RexSubQuery.in(inner, com.google.common.collect.ImmutableList.of(beforeRef)));

        var afterBase = RuleBuilder.create();
        var afterOuterName = RuleChecks.table(afterBase, 1, false);
        var afterInnerName = RuleChecks.table(afterBase, 1, false);
        RelBuilder afterBuilder = RuleChecks.ruleBuilder(afterBase);
        RelNode afterOuter = afterBuilder.scan(afterOuterName).build();
        RelNode afterInner = afterBuilder.scan(afterInnerName).build();
        var rexBuilder = afterBuilder.getRexBuilder();
        var correlationId = afterBuilder.getCluster().createCorrel();
        var correlated = rexBuilder.makeCorrel(afterOuter.getRowType(), correlationId);
        RexNode equality = rexBuilder.makeCall(SqlStdOperatorTable.EQUALS,
                rexBuilder.makeFieldAccess(correlated, 0), rexBuilder.makeInputRef(afterInner, 0));
        RelNode matchingRows = LogicalFilter.create(afterInner, equality);
        RexNode exists = RexSubQuery.exists(matchingRows);
        RelNode after = LogicalFilter.create(afterOuter, exists, ImmutableSet.of(correlationId));

        RuleChecks.rewrites(rule, before, after);

        var nearMissBase = RuleBuilder.create();
        var nearOuterName = RuleChecks.table(nearMissBase, 1, false);
        var nearInnerName = RuleChecks.table(nearMissBase, 1, false);
        RelBuilder nearMissBuilder = RuleChecks.ruleBuilder(nearMissBase);
        RelNode nearOuter = nearMissBuilder.scan(nearOuterName).build();
        RelNode nearInner = nearMissBuilder.scan(nearInnerName).build();
        RexNode positiveAny = RexSubQuery.in(nearInner,
                com.google.common.collect.ImmutableList.of(nearMissBuilder.getRexBuilder().makeInputRef(nearOuter, 0)));
        RelNode notAny = LogicalFilter.create(nearOuter,
                nearMissBuilder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT, positiveAny));
        RuleChecks.doesNotApply(rule, notAny);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
