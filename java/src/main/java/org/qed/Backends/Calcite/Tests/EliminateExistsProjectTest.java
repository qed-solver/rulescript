package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexSubQuery;
import org.qed.RRuleInstances.EliminateExistsProject;
import org.qed.RuleBuilder;

import java.util.List;

public class EliminateExistsProjectTest {

    public static void runTest() {
        var rule = new EliminateExistsProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateExistsProject rule) {
        var builder = RuleBuilder.create();
        var outer = RuleChecks.table(builder, 1, false);
        var input = RuleChecks.table(builder, 1, false);
        var wide = RuleChecks.table(builder, 2, false);
        var projected = builder.scan(input).project(List.of(builder.field(0)), List.of(), true).build();
        var before = builder.scan(outer).filter(RexSubQuery.exists(projected)).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(outer).filter(RexSubQuery.exists(plain.scan(input).build())).build();
        RuleChecks.rewrites(rule, before, after);

        var widened = builder.scan(wide).project(List.of(builder.field(0), builder.field(1)), List.of(), true).build();
        RuleChecks.doesNotApply(rule, builder.scan(outer).filter(RexSubQuery.exists(widened)).build());
        var bare = builder.scan(input).build();
        RuleChecks.doesNotApply(rule, builder.scan(outer).filter(RexSubQuery.exists(bare)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateExistsProject test...");
        runTest();
    }
}
