package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterRemoveIsNotDistinctFrom;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterRemoveIsNotDistinctFromTest {

    public static void runTest() {
        var rule = new FilterRemoveIsNotDistinctFrom();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FilterRemoveIsNotDistinctFrom rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var crossJoin = (java.util.function.Supplier<RelBuilder>) () ->
                b.scan(l).scan(r).join(JoinRelType.INNER, b.literal(true));
        var before = crossJoin.get()
                .filter(b.call(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, b.field(0), b.field(1))).build();
        crossJoin.get();
        var after = b.filter(b.or(b.and(b.isNull(b.field(0)), b.isNull(b.field(1))),
                b.call(SqlStdOperatorTable.IS_TRUE, RuleChecks.eq(b, b.field(0), b.field(1))))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, crossJoin.get()
                .filter(b.call(SqlStdOperatorTable.IS_DISTINCT_FROM, b.field(0), b.field(1))).build());
        RuleChecks.doesNotApply(rule, crossJoin.get().filter(RuleChecks.eq(b, b.field(0), b.field(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FilterRemoveIsNotDistinctFrom test...");
        runTest();
    }
}
