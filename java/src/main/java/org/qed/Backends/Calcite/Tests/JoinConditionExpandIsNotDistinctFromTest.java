package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinConditionExpandIsNotDistinctFrom;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinConditionExpandIsNotDistinctFromTest {

    public static void runTest() {
        var rule = new JoinConditionExpandIsNotDistinctFrom();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinConditionExpandIsNotDistinctFrom rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var joinOn = (java.util.function.BiFunction<JoinRelType, org.apache.calcite.sql.SqlOperator, RelBuilder>)
                (type, op) -> b.scan(l).scan(r).join(type, b.call(op, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = joinOn.apply(JoinRelType.INNER, SqlStdOperatorTable.IS_NOT_DISTINCT_FROM).build();
        b.scan(l).scan(r);
        var after = b.join(JoinRelType.INNER, b.or(b.and(b.isNull(b.field(2, 0, 0)), b.isNull(b.field(2, 1, 0))),
                b.call(SqlStdOperatorTable.IS_TRUE, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, joinOn.apply(JoinRelType.INNER, SqlStdOperatorTable.IS_DISTINCT_FROM).build());
        RuleChecks.doesNotApply(rule, joinOn.apply(JoinRelType.INNER, SqlStdOperatorTable.EQUALS).build());
        RuleChecks.doesNotApply(rule, joinOn.apply(JoinRelType.LEFT, SqlStdOperatorTable.IS_NOT_DISTINCT_FROM).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinConditionExpandIsNotDistinctFrom test...");
        runTest();
    }
}
