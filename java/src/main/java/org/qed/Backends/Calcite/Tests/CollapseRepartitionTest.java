package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.CollapseRepartition;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public final class CollapseRepartitionTest {
    public static void runTest() {
        var rule = new CollapseRepartition();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var name = table(builder, 3, false);
        var b = ruleBuilder(builder);
        var source = b.scan(name).build();
        b.push(source);
        b.project(java.util.List.of(b.field(0), b.field(1), b.field(2)), java.util.List.of("EXPR$0", "EXPR$1", "EXPR$2"), true);
        var before = b.build();
        b.push(before);
        b.project(java.util.List.of(b.field(0), b.field(1), b.field(2)), java.util.List.of("EXPR$0", "EXPR$1", "EXPR$2"), true);
        var stacked = b.build();
        b.push(source);
        var expectedFields = new java.util.ArrayList<org.apache.calcite.rex.RexNode>();
        for (var i = 0; i < source.getRowType().getFieldCount(); i++) {
            expectedFields.add(b.getRexBuilder().makeCast(source.getRowType().getFieldList().get(i).getType(), b.field(i), true));
        }
        b.project(expectedFields, java.util.List.of("EXPR$0", "EXPR$1", "EXPR$2"), true);
        var expected = b.build();
        rewrites(rule, stacked, expected);

        var narrowBuilder = RuleBuilder.create();
        var narrowName = table(narrowBuilder, 2, false);
        var nearMiss = ruleBuilder(narrowBuilder).scan(narrowName).build();
        doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running CollapseRepartition test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("CollapseRepartition test failures: " + CalciteTester.failures());
        }
    }
}
