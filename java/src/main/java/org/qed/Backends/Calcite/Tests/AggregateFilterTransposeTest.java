package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateFilterTranspose;
import org.qed.RuleBuilder;

public class AggregateFilterTransposeTest {
    public static void runTest() {
        var rule = new AggregateFilterTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false, false);
        var right = RuleChecks.table(builder, 1, false, false);
        var b = RuleChecks.ruleBuilder(builder);
        var predicate = builder.genericPredicateOp("keep_group", false);
        var aggregate = builder.genericAggregateOp("sum_value", org.qed.RelType.fromString("INTEGER", true));
        var source = b.scan(left).scan(right).join(JoinRelType.INNER, b.literal(true)).build();
        var before = b.push(source)
                .filter(b.call(predicate, b.field(0)))
                .aggregate(b.groupKey(b.field(0)), b.aggregateCall(aggregate, false, null, "sum_value", b.field(1)))
                .build();
        var expected = b.scan(left).scan(right).join(JoinRelType.INNER, b.literal(true))
                .aggregate(b.groupKey(b.field(0)), b.aggregateCall(aggregate, false, null, "sum_value", b.field(1)))
                .filter(b.call(predicate, b.field(0))).build();
        RuleChecks.rewrites(rule, before, expected);
        var nearMiss = b.push(source)
                .filter(RuleChecks.gt(b, b.field(1), 3))
                .aggregate(b.groupKey(b.field(0)), b.aggregateCall(aggregate, false, null, "sum_value", b.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateFilterTranspose test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError(CalciteTester.failures() + " test failures");
        }
        System.out.println("AggregateFilterTranspose: 0 test failures");
    }
}
