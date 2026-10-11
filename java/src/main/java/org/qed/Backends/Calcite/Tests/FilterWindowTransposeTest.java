package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterWindowTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterWindowTransposeTest {

    public static void runTest() {
        var rule = new FilterWindowTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FilterWindowTranspose rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var window = (java.util.function.Supplier<RelBuilder>) () -> b.scan(t)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1)));
        var before = window.get().filter(b.and(RuleChecks.gt(b, b.field(0), 5),
                b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.field(1)))).build();
        var after = b.scan(t).filter(RuleChecks.gt(b, b.field(0), 5))
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1)))
                .filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.field(1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, window.get().filter(b.and(RuleChecks.gt(b, b.field(1), 5),
                b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.field(1)))).build());
        RuleChecks.doesNotApply(rule, window.get().filter(RuleChecks.gt(b, b.field(0), 5)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FilterWindowTranspose test...");
        runTest();
    }
}
