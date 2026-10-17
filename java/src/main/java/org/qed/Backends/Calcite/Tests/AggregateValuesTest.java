package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.AggregateValues;
import org.qed.RuleBuilder;

import java.util.List;

import static org.qed.Backends.Calcite.Tests.RuleChecks.doesNotApply;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewrites;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesConcretePredicates;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesPattern;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rejectsNearMisses;
import static org.qed.Backends.Calcite.Tests.RuleChecks.ruleBuilder;
import static org.qed.Backends.Calcite.Tests.RuleChecks.table;

public class AggregateValuesTest {
    public static void runTest() {
        var rule = new AggregateValues();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);
        rewrites(rule, before(), after());
        doesNotApply(rule, grandTotal());
    }

    private static RelNode before() {
        var config = RuleBuilder.create();
        var name = table(config, 1, false, true);
        var builder = ruleBuilder(config);
        var source = builder.scan(name).build();
        return LogicalAggregate.create(source, List.of(), ImmutableBitSet.of(0), null, List.of());
    }

    private static RelNode after() {
        var config = RuleBuilder.create();
        var name = table(config, 1, false, true);
        var builder = ruleBuilder(config);
        var left = builder.scan(name).build();
        var right = builder.scan(name).build();
        return LogicalUnion.create(List.of(left, right), false);
    }

    private static RelNode grandTotal() {
        var config = RuleBuilder.create();
        var name = table(config, 1, false, true);
        var builder = ruleBuilder(config);
        var source = builder.scan(name).build();
        return LogicalAggregate.create(source, List.of(), ImmutableBitSet.of(), null, List.of());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateValues test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("AggregateValues test failed");
        }
    }
}
