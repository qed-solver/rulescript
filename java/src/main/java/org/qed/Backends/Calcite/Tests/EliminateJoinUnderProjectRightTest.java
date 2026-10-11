package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.EliminateJoinUnderProjectRight;
import org.qed.RuleBuilder;

public class EliminateJoinUnderProjectRightTest {

    public static void runTest() {
        var rule = new EliminateJoinUnderProjectRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateJoinUnderProjectRight rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, true, false);
        var other = RuleChecks.table(base, 1, true, false);
        var plainKey = RuleChecks.table(base, 1, false, false);
        var nullableKey = RuleChecks.table(base, 1, true, true);
        var b = RuleChecks.ruleBuilder(base);
        var before = selfJoin(b, t, JoinRelType.INNER).project(b.field(1)).build();
        var after = b.scan(t).project(b.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, selfJoin(b, plainKey, JoinRelType.INNER).project(b.field(1)).build());
        RuleChecks.doesNotApply(rule, selfJoin(b, nullableKey, JoinRelType.INNER).project(b.field(1)).build());
        RuleChecks.doesNotApply(rule, selfJoin(b, t, JoinRelType.LEFT).project(b.field(1)).build());
        RuleChecks.doesNotApply(rule, selfJoin(b, t, JoinRelType.INNER).project(b.field(0)).build());
        RuleChecks.doesNotApply(rule, b.scan(t).scan(other)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).project(b.field(1))
                .build());
    }

    private static RelBuilder selfJoin(RelBuilder b, String table, JoinRelType type) {
        return b.scan(table).scan(table).join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinUnderProjectRight test...");
        runTest();
    }
}
