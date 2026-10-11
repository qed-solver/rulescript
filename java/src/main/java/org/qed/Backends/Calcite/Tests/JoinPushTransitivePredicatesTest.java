package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinPushTransitivePredicates;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinPushTransitivePredicatesTest {

    public static void runTest() {
        var rule = new JoinPushTransitivePredicates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinPushTransitivePredicates rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false, false);
        var r = RuleChecks.table(base, 1, false, false);
        var nullableL = RuleChecks.table(base, 1, false, true);
        var nullableR = RuleChecks.table(base, 1, false, true);
        var b = RuleChecks.ruleBuilder(base);
        var join = (java.util.function.BiFunction<String, String, RelBuilder>) (left, right) -> b.scan(left)
                .scan(right).filter(RuleChecks.gt(b, b.field(0), 5))
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = join.apply(l, r).build();
        var after = b.scan(l).filter(RuleChecks.gt(b, b.field(0), 5)).scan(r).filter(RuleChecks.gt(b, b.field(0), 5))
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, join.apply(nullableL, nullableR).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinPushTransitivePredicates test...");
        runTest();
    }
}
