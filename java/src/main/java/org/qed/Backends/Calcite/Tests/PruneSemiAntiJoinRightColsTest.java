package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.PruneSemiAntiJoinRightCols;
import org.qed.RuleBuilder;

public class PruneSemiAntiJoinRightColsTest {

    public static void runTest() {
        var rule = new PruneSemiAntiJoinRightCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneSemiAntiJoinRightCols rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 2, false);
        var single = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = join(b, l, r, JoinRelType.ANTI).build();
        var after = b.scan(l).scan(r).project(b.field(0))
                .join(JoinRelType.ANTI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, join(b, l, r, JoinRelType.SEMI).build());
        RuleChecks.doesNotApply(rule, join(b, l, single, JoinRelType.ANTI).build());
    }

    private static RelBuilder join(RelBuilder b, String left, String right, JoinRelType type) {
        return b.scan(left).scan(right).join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
    }

    public static void main(String[] args) {
        System.out.println("Running PruneSemiAntiJoinRightCols test...");
        runTest();
    }
}
