package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.EliminateJoinUnderProjectLeft;
import org.qed.RuleBuilder;

public class EliminateJoinUnderProjectLeftTest {

    public static void runTest() {
        var rule = new EliminateJoinUnderProjectLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateJoinUnderProjectLeft rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, true, false);
        var plainKey = RuleChecks.table(base, 1, false, false);
        var nullableKey = RuleChecks.table(base, 1, true, true);
        var b = RuleChecks.ruleBuilder(base);
        var selfLeftJoin = (java.util.function.Function<String, RelBuilder>) table -> b.scan(table).scan(table)
                .join(JoinRelType.LEFT, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = selfLeftJoin.apply(t).project(b.field(0)).build();
        var after = b.scan(t).project(b.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, selfLeftJoin.apply(plainKey).project(b.field(0)).build());
        RuleChecks.doesNotApply(rule, selfLeftJoin.apply(nullableKey).project(b.field(0)).build());
        RuleChecks.doesNotApply(rule, selfLeftJoin.apply(t).project(b.field(1)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinUnderProjectLeft test...");
        runTest();
    }
}
