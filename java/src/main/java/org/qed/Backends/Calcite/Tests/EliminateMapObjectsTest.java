package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.EliminateMapObjects;

public final class EliminateMapObjectsTest {
    public static void runTest() {
        var rule = new EliminateMapObjects();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var input = builder.scan(table).build();
        var positive = org.apache.calcite.rel.logical.LogicalProject.create(input, java.util.List.of(),
                java.util.List.of(builder.getRexBuilder().makeInputRef(input, 0)),
                input.getRowType().getFieldNames());
        var expectedBuilder = org.qed.RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false, false);
        var expected = expectedBuilder.scan(expectedTable).build();
        RuleChecks.rewrites(rule, positive, expected);

        var nullableBuilder = org.qed.RuleBuilder.create();
        var nullableTable = RuleChecks.table(nullableBuilder, 1, false, true);
        var nullableInput = nullableBuilder.scan(nullableTable).build();
        var nullable = org.apache.calcite.rel.logical.LogicalProject.create(nullableInput, java.util.List.of(),
                java.util.List.of(nullableBuilder.getRexBuilder().makeInputRef(nullableInput, 0)),
                nullableInput.getRowType().getFieldNames());
        RuleChecks.doesNotApply(rule, nullable);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateMapObjects test...");
        runTest();
    }
}
