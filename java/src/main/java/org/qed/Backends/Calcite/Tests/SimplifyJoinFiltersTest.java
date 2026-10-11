package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyJoinFilters;
import org.qed.RuleBuilder;

public class SimplifyJoinFiltersTest {

    public static void runTest() {
        var rule = new SimplifyJoinFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        dropsTrueOnItem(rule);
    }

    private static void dropsTrueOnItem(SimplifyJoinFilters rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var on = builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0));
        var before = builder.scan(l).scan(r).join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.AND, on, builder.literal(true))).build();

        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l).scan(r).join(JoinRelType.INNER,
                plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0))).build();
        RuleChecks.rewrites(rule, before, after);

        var noTrue = RuleChecks.ruleBuilder(builder);
        noTrue.scan(l).scan(r);
        var withoutTrue = noTrue.join(JoinRelType.INNER,
                noTrue.call(SqlStdOperatorTable.EQUALS, noTrue.field(2, 0, 0), noTrue.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(rule, withoutTrue);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyJoinFilters test...");
        runTest();
    }
}
