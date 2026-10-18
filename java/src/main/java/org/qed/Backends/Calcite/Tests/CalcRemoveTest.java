package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.qed.Backends.Calcite.Tests.RuleChecks;
import org.qed.RRuleInstances.CalcRemove;
import org.qed.RuleBuilder;

import java.util.List;

public final class CalcRemoveTest {
    public static void runTest() {
        var rule = new CalcRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 2, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        RelNode source = builder.scan(table).build();
        var before = builder.push(source).project(List.of(builder.field(0), builder.field(1)), List.of(), true).build();
        RelNode after = builder.scan(table).build();
        RuleChecks.rewrites(rule, before, after);

        var nearRuleBuilder = RuleBuilder.create();
        var nearTable = RuleChecks.table(nearRuleBuilder, 2, false);
        var nearBuilder = RuleChecks.ruleBuilder(nearRuleBuilder);
        RelNode nearSource = nearBuilder.scan(nearTable).build();
        var nearMiss = nearBuilder.push(nearSource)
                .project(List.of(nearBuilder.field(1), nearBuilder.field(0)), List.of(), true).build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running CalcRemove test...");
        runTest();
    }
}
