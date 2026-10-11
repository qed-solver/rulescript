package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SortFiltersInJoin;
import org.qed.RuleBuilder;

public class SortFiltersInJoinTest {

    public static void runTest() {
        var rule = new SortFiltersInJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SortFiltersInJoin rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(l).scan(r).join(JoinRelType.INNER, b.and(
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))).build();
        var after = b.scan(l).scan(r).join(JoinRelType.INNER, b.and(
                b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)),
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))).build();
        RuleChecks.rewrites(rule, before, after);

        var single = b.scan(l).scan(r).join(JoinRelType.INNER,
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(rule, single);

        var outer = b.scan(l).scan(r).join(JoinRelType.LEFT, b.and(
                RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)),
                b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))).build();
        RuleChecks.doesNotApply(rule, outer);
    }

    public static void main(String[] args) {
        System.out.println("Running SortFiltersInJoin test...");
        runTest();
    }
}
