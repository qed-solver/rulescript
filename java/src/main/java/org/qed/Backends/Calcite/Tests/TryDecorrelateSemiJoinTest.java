package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.TryDecorrelateSemiJoin;
import org.qed.RuleBuilder;

public class TryDecorrelateSemiJoinTest {

    public static void runTest() {
        var rule = new TryDecorrelateSemiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(TryDecorrelateSemiJoin rule) {
        var base = RuleBuilder.create();
        var unique = RuleChecks.table(base, 1, true);
        var plain = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var keyed = b.scan(unique).build();
        var other = b.scan(plain).build();
        var before = RuleChecks.correlate(b, keyed, other, JoinRelType.SEMI,
                args -> RuleChecks.eq(b, args[0], args[1]));
        var inner = RuleChecks.correlate(b, keyed, other, JoinRelType.INNER,
                args -> RuleChecks.eq(b, args[0], args[1]));
        var after = b.push(inner).aggregate(b.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        var nonUnique = b.scan(plain).build();
        RuleChecks.doesNotApply(rule, RuleChecks.correlate(b, nonUnique, other, JoinRelType.SEMI,
                args -> RuleChecks.eq(b, args[0], args[1])));
    }

    public static void main(String[] args) {
        System.out.println("Running TryDecorrelateSemiJoin test...");
        runTest();
    }
}
