package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AssociateLimitJoinsRight;
import org.qed.RuleBuilder;

public final class AssociateLimitJoinsRightTest {
    public static void runTest() {
        var rule = new AssociateLimitJoinsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var a = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.table(builder, 1, false);
        var c = RuleChecks.table(builder, 1, false);
        var pab = builder.genericPredicateOp("p_ab", true);
        var pac = builder.genericPredicateOp("p_ac", true);
        var beforeBuilder = RuleChecks.ruleBuilder(builder);
        var before = beforeBuilder.scan(c).scan(a).scan(b)
                .join(JoinRelType.LEFT, beforeBuilder.call(pab, CalciteRuntime.field(beforeBuilder, 2, 0), CalciteRuntime.field(beforeBuilder, 2, 1)))
                .join(JoinRelType.INNER, beforeBuilder.call(pac, CalciteRuntime.field(beforeBuilder, 2, 1), CalciteRuntime.field(beforeBuilder, 2, 0)))
                .build();
        var expectedBuilder = RuleChecks.ruleBuilder(builder);
        var expected = expectedBuilder.scan(a).scan(c)
                .join(JoinRelType.INNER, expectedBuilder.call(pac, CalciteRuntime.field(expectedBuilder, 2, 0), CalciteRuntime.field(expectedBuilder, 2, 1)))
                .scan(b)
                .join(JoinRelType.LEFT, expectedBuilder.call(pab, CalciteRuntime.field(expectedBuilder, 2, 0), CalciteRuntime.field(expectedBuilder, 2, 2)))
                .project(Seq.of(CalciteRuntime.field(expectedBuilder, 1, 1), CalciteRuntime.field(expectedBuilder, 1, 0), CalciteRuntime.field(expectedBuilder, 1, 2)).asJava())
                .build();
        RuleChecks.rewrites(rule, before, expected);

        var nearMissBuilder = RuleChecks.ruleBuilder(builder);
        var nearMiss = nearMissBuilder.scan(c).scan(a).scan(b)
                .join(JoinRelType.LEFT, nearMissBuilder.call(pab, CalciteRuntime.field(nearMissBuilder, 2, 0), CalciteRuntime.field(nearMissBuilder, 2, 1)))
                .join(JoinRelType.LEFT, nearMissBuilder.call(pac, CalciteRuntime.field(nearMissBuilder, 2, 1), CalciteRuntime.field(nearMissBuilder, 2, 0)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running AssociateLimitJoinsRight test...");
        runTest();
    }
}
