package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.PushSelectIntoWindow;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PushSelectIntoWindowTest {

    public static void runTest() {
        var rule = new PushSelectIntoWindow();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoWindow rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 2, false);
        var w = base.genericAggregateOp("w", RelType.fromString("INTEGER", true));
        var b = RuleChecks.ruleBuilder(base);

        var input = base.scan(t).build();
        var perPart = base.push(input)
                .aggregate(base.groupKey(0), base.aggregateCall(w, base.field(1)))
                .build();
        var window = base.push(input).push(perPart)
                .join(JoinRelType.INNER, RuleChecks.eq(base, base.field(2, 0, 0), base.field(2, 1, 0)))
                .build();
        var before = base.push(window)
                .filter(base.and(RuleChecks.gt(base, base.field(0), 5), RuleChecks.gt(base, base.field(1), 10)))
                .build();

        var filteredInput = b.scan(t).filter(RuleChecks.gt(b, b.field(0), 5)).build();
        var filteredPerPart = b.push(filteredInput)
                .aggregate(b.groupKey(0), b.aggregateCall(w, b.field(1)))
                .build();
        var filteredWindow = b.push(filteredInput).push(filteredPerPart)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        var after = b.push(filteredWindow).filter(RuleChecks.gt(b, b.field(1), 10)).build();
        RuleChecks.rewrites(rule, before, after);

        var leftWindow = base.push(input).push(perPart)
                .join(JoinRelType.LEFT, RuleChecks.eq(base, base.field(2, 0, 0), base.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, base.push(leftWindow)
                .filter(base.and(RuleChecks.gt(base, base.field(0), 5), RuleChecks.gt(base, base.field(1), 10)))
                .build());
        RuleChecks.doesNotApply(rule, base.push(window)
                .filter(RuleChecks.gt(base, base.field(0), 5))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoWindow test...");
        runTest();
    }
}
