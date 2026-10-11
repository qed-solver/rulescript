package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldGroupingOperators;
import org.qed.RuleBuilder;

import java.util.List;

public class FoldGroupingOperatorsTest {

    public static void runTest() {
        var rule = new FoldGroupingOperators();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldGroupingOperators rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t)
                .project(List.of(b.field(0)), List.of("k"), true)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(0)))
                .aggregate(b.groupKey(0))
                .build();
        var after = b.scan(t).aggregate(b.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        var outerWithCall = b.scan(t)
                .project(List.of(b.field(0)), List.of("k"), true)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(0)))
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MAX, b.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, outerWithCall);

        var plain = b.scan(t).aggregate(b.groupKey(0)).build();
        RuleChecks.doesNotApply(rule, plain);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldGroupingOperators test...");
        runTest();
    }
}
