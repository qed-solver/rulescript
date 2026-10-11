package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.UnionPullUpConstants;
import org.qed.RuleBuilder;

import java.util.List;

public class UnionPullUpConstantsTest {

    public static void runTest() {
        var rule = new UnionPullUpConstants();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(UnionPullUpConstants rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 2, false);
        var tb = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var constant = (java.util.function.BiFunction<Boolean, Boolean, RelBuilder>) (first, second) ->
                b.scan(ta).project(List.of(b.literal(first), b.field(1)), List.of(), true).scan(tb)
                        .project(List.of(b.literal(second), b.field(1)), List.of(), true);
        var before = constant.apply(true, true).union(true, 2).build();
        var after = b.scan(ta).project(b.field(1)).scan(tb).project(b.field(1)).union(true, 2)
                .project(b.literal(true), b.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, constant.apply(true, false).union(true, 2).build());
        RuleChecks.doesNotApply(rule, constant.apply(true, true).union(false, 2).build());
    }

    public static void main(String[] args) {
        System.out.println("Running UnionPullUpConstants test...");
        runTest();
    }
}
