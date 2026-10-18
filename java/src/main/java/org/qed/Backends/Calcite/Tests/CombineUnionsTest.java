package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.CombineUnions;
import org.qed.RuleBuilder;

import java.util.List;

public class CombineUnionsTest {
    public static void runTest() {
        var rule = new CombineUnions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltMatch(rule);
        handBuiltNearMiss(rule);
    }

    private static void handBuiltMatch(CombineUnions rule) {
        var builder = RuleBuilder.create();
        var aName = RuleChecks.table(builder, 1, false);
        var bName = RuleChecks.table(builder, 1, false);
        var cName = RuleChecks.table(builder, 1, false);
        var dName = RuleChecks.table(builder, 1, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        var a = relBuilder.scan(aName).build();
        var b = relBuilder.scan(bName).build();
        var c = relBuilder.scan(cName).build();
        var d = relBuilder.scan(dName).build();
        var innerUnion = LogicalUnion.create(List.of(b, c), true);
        var innerDistinct = distinct(innerUnion);
        var outerUnion = LogicalUnion.create(List.of(a, innerDistinct, d), true);
        var before = distinct(outerUnion);
        var expectedUnion = LogicalUnion.create(List.of(a, b, c, d), true);
        var expected = distinct(expectedUnion);
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void handBuiltNearMiss(CombineUnions rule) {
        var builder = RuleBuilder.create();
        var aName = RuleChecks.table(builder, 1, false);
        var bName = RuleChecks.table(builder, 1, false);
        var cName = RuleChecks.table(builder, 1, false);
        var dName = RuleChecks.table(builder, 1, false);
        var relBuilder = RuleChecks.ruleBuilder(builder);
        var a = relBuilder.scan(aName).build();
        var b = relBuilder.scan(bName).build();
        var c = relBuilder.scan(cName).build();
        var d = relBuilder.scan(dName).build();
        var innerSetUnion = LogicalUnion.create(List.of(b, c), false);
        var outerUnion = LogicalUnion.create(List.of(a, distinct(innerSetUnion), d), true);
        RuleChecks.doesNotApply(rule, distinct(outerUnion));
    }

    private static RelNode distinct(RelNode input) {
        return LogicalAggregate.create(input, List.of(), ImmutableBitSet.of(0), null, List.of());
    }

    public static void main(String[] args) {
        System.out.println("Running CombineUnions test...");
        runTest();
    }
}
