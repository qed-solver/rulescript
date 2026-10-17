package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateProjectPullUpConstants;

public class AggregateProjectPullUpConstantsTest {
    public static void runTest() {
        var rule = new AggregateProjectPullUpConstants();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        var input = b.scan(left).project(Seq.of(b.literal(true), b.field(0))).build();
        var before = b.push(input).aggregate(b.groupKey(b.field(0), b.field(1)),
                b.count(false, "f", b.field(1))).build();
        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        var expectedInput = expectedBuilder.scan(left).project(Seq.of(expectedBuilder.literal(true), expectedBuilder.field(0))).build();
        var reduced = expectedBuilder.push(expectedInput).aggregate(expectedBuilder.groupKey(expectedBuilder.field(1)),
                expectedBuilder.count(false, "f", expectedBuilder.field(1))).build();
        var expected = expectedBuilder.push(reduced).project(Seq.of(expectedBuilder.literal(true), expectedBuilder.field(0), expectedBuilder.field(1))).build();
        RuleChecks.rewrites(rule, before, expected);

        var nearBuilder = RuleChecks.ruleBuilder(builder);
        var nearMissInput = nearBuilder.scan(left).project(Seq.of(nearBuilder.literal(false), nearBuilder.field(0))).build();
        var nearMiss = nearBuilder.push(nearMissInput).aggregate(nearBuilder.groupKey(nearBuilder.field(0), nearBuilder.field(1)),
                nearBuilder.count(false, "f", nearBuilder.field(1))).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateProjectPullUpConstants test...");
        runTest();
    }
}
