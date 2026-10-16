package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.qed.RRuleInstances.SemiJoinRemove;
import org.qed.RuleBuilder;

public class SemiJoinRemoveTest {

    public static void runTest() {
        var rule = new SemiJoinRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SemiJoinRemove rule) {
        var base = RuleBuilder.create();
        var x = RuleChecks.table(base, 1, false);
        var y = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var cond = (java.util.function.Supplier<RexNode>) () -> RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0));
        var before = b.scan(x).scan(y).join(JoinRelType.SEMI, cond.get()).scan(y).join(JoinRelType.INNER, cond.get()).build();
        var after = b.scan(x).scan(y).join(JoinRelType.INNER, cond.get()).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(x).scan(y).join(JoinRelType.INNER, cond.get())
                .scan(y).join(JoinRelType.INNER, cond.get()).build());
        RuleChecks.doesNotApply(rule, b.scan(x).scan(y).join(JoinRelType.SEMI, cond.get())
                .scan(y).join(JoinRelType.LEFT, cond.get()).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SemiJoinRemove test...");
        runTest();
    }
}
