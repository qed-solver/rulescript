package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinAddRedundantSemiJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinAddRedundantSemiJoinTest {

    public static void runTest() {
        var rule = new JoinAddRedundantSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinAddRedundantSemiJoin rule) {
        var base = RuleBuilder.create();
        var x = RuleChecks.table(base, 1, false);
        var y = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var cond = (java.util.function.Function<RelBuilder, RexNode>) q ->
                RuleChecks.eq(q, q.field(2, 0, 0), q.field(2, 1, 0));
        var before = b.scan(x).scan(y).join(JoinRelType.INNER, cond.apply(b)).build();
        b.scan(x).scan(y).join(JoinRelType.SEMI, cond.apply(b)).scan(y);
        var after = b.join(JoinRelType.INNER, cond.apply(b)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(x).scan(y).join(JoinRelType.LEFT, cond.apply(b)).build());
        RuleChecks.doesNotApply(rule, b.scan(x).scan(y).join(JoinRelType.SEMI, cond.apply(b)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinAddRedundantSemiJoin test...");
        runTest();
    }
}
