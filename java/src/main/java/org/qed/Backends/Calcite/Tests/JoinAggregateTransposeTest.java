package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinAggregateTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinAggregateTransposeTest {

    public static void runTest() {
        var rule = new JoinAggregateTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinAggregateTranspose rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var keyed = RuleChecks.table(base, 1, true);
        var plain = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var joinedWith = (java.util.function.BiFunction<String, java.util.function.BiFunction<RelBuilder, Integer,
                RexNode>, RelBuilder>) (right, cond) -> b.scan(l)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1))).scan(right)
                .join(JoinRelType.INNER, cond.apply(b, 1));
        java.util.function.BiFunction<RelBuilder, Integer, RexNode> onGroupKey =
                (q, other) -> RuleChecks.eq(q, q.field(2, 0, 0), q.field(2, 1, 0));
        var before = joinedWith.apply(keyed, onGroupKey).build();
        var after = b.scan(l).scan(keyed).join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .aggregate(b.groupKey(0, 2), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1)))
                .project(b.field(0), b.field(2), b.field(1)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, joinedWith.apply(plain, onGroupKey).build());
        RuleChecks.doesNotApply(rule, joinedWith.apply(keyed,
                (q, other) -> RuleChecks.eq(q, q.field(2, 0, 1), q.field(2, 1, 0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinAggregateTranspose test...");
        runTest();
    }
}
