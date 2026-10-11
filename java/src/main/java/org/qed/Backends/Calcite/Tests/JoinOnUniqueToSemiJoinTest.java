package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinOnUniqueToSemiJoin;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinOnUniqueToSemiJoinTest {

    public static void runTest() {
        var rule = new JoinOnUniqueToSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinOnUniqueToSemiJoin rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var keyed = RuleChecks.table(base, 1, true);
        var plain = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var projected = (java.util.function.BiFunction<JoinRelType, String, RelBuilder>) (type, right) ->
                b.scan(l).scan(right).join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = projected.apply(JoinRelType.INNER, keyed).project(b.field(0)).build();
        var after = b.scan(l).scan(keyed).join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, projected.apply(JoinRelType.INNER, plain).project(b.field(0)).build());
        RuleChecks.doesNotApply(rule, projected.apply(JoinRelType.LEFT, keyed).project(b.field(0)).build());
        RuleChecks.doesNotApply(rule, projected.apply(JoinRelType.INNER, keyed).project(b.field(1)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinOnUniqueToSemiJoin test...");
        runTest();
    }
}
