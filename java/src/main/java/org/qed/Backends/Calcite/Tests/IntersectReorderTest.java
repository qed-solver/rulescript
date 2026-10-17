package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.IntersectReorder;
import org.qed.RuleBuilder;

import java.util.List;

public final class IntersectReorderTest {
    public static void runTest() {
        var rule = new IntersectReorder();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        reordersIndependentSetOp();
        doesNotMatchIntersectAll();
    }

    private static void reordersIndependentSetOp() {
        var ruleBuilder = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var a = RuleChecks.table(ruleBuilder, 1, false, false);
        var b = RuleChecks.table(ruleBuilder, 1, false, false);
        var c = RuleChecks.table(ruleBuilder, 1, false, false);
        var inputA = builder.scan(a).build();
        var inputB = builder.scan(b).build();
        var inputC = builder.scan(c).build();
        RelNode before = LogicalIntersect.create(List.of(inputA, inputB, inputC), false);
        RelNode expected = LogicalIntersect.create(List.of(inputC, inputA, inputB), false);
        RuleChecks.rewrites(new IntersectReorder(), before, expected);
    }

    private static void doesNotMatchIntersectAll() {
        var ruleBuilder = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var a = RuleChecks.table(ruleBuilder, 1, false, false);
        var b = RuleChecks.table(ruleBuilder, 1, false, false);
        var c = RuleChecks.table(ruleBuilder, 1, false, false);
        RelNode all = LogicalIntersect.create(List.of(builder.scan(a).build(), builder.scan(b).build(),
                builder.scan(c).build()), true);
        RuleChecks.doesNotApply(new IntersectReorder(), all);
    }

    public static void main(String[] args) {
        System.out.println("Running IntersectReorder test...");
        runTest();
    }
}
