package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateToSemiJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateToSemiJoinTest {

    public static void runTest() {
        var rule = new AggregateToSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateToSemiJoin rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var joinOn = (java.util.function.BiFunction<JoinRelType, org.apache.calcite.sql.SqlOperator, RelBuilder>)
                (type, op) -> b.scan(l).scan(r).aggregate(b.groupKey(0))
                        .join(type, b.call(op, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = joinOn.apply(JoinRelType.INNER, SqlStdOperatorTable.EQUALS).aggregate(b.groupKey(0)).build();
        var after = b.scan(l).scan(r)
                .join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .aggregate(b.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule,
                joinOn.apply(JoinRelType.INNER, SqlStdOperatorTable.LESS_THAN).aggregate(b.groupKey(0)).build());
        RuleChecks.doesNotApply(rule,
                joinOn.apply(JoinRelType.LEFT, SqlStdOperatorTable.EQUALS).aggregate(b.groupKey(0)).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .aggregate(b.groupKey(0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateToSemiJoin test...");
        runTest();
    }
}
