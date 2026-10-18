package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.SetOpToFilter;
import org.qed.RuleBuilder;

import static org.qed.Backends.Calcite.Tests.RuleChecks.*;

public class SetOpToFilterTest {
    public static void runTest() {
        var rule = new SetOpToFilter();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        var qedBuilder = RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 1, false, false);
        var builder = ruleBuilder(qedBuilder);
        var left = builder.scan(table).filter(gt(builder, builder.field(0), 10)).build();
        var right = builder.scan(table).filter(gt(builder, builder.field(0), 20)).build();
        var before = builder.push(left).push(right).union(false, 2).build();
        var expectedBuilder = builder;
        expectedBuilder.scan(table);
        var expected = expectedBuilder.filter(expectedBuilder.or(gt(expectedBuilder, expectedBuilder.field(0), 10),
                        gt(expectedBuilder, expectedBuilder.field(0), 20)))
                .aggregate(expectedBuilder.groupKey(expectedBuilder.field(0))).build();
        rewrites(rule, before, expected);

        var noMatchBuilder = builder;
        var left2 = noMatchBuilder.scan(table).filter(gt(noMatchBuilder, noMatchBuilder.field(0), 10)).build();
        var right2 = noMatchBuilder.scan(table).filter(gt(noMatchBuilder, noMatchBuilder.field(0), 20)).build();
        var unionAll = noMatchBuilder.push(left2).push(right2).union(true, 2).build();
        doesNotApply(rule, unionAll);
    }

    public static void main(String[] args) {
        System.out.println("Running SetOpToFilter test...");
        runTest();
    }
}
