package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.RRuleInstances.PushAssignmentCastsIntoValues;
import org.qed.RuleBuilder;

import java.util.List;

public class PushAssignmentCastsIntoValuesTest {
    public static void runTest() {
        var rule = new PushAssignmentCastsIntoValues();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushAssignmentCastsIntoValues rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var input = builder.scan(table).build();
        var value = builder.getRexBuilder().makeInputRef(input, 0);
        var cast = builder.getRexBuilder().makeCast(builder.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.BIGINT), value);
        var before = LogicalProject.create(input, List.of(), List.of(cast, builder.literal(true)), List.of("casted", "other"));
        var plain = RuleChecks.ruleBuilder(builder);
        var expectedInput = plain.scan(table).build();
        var expectedRef = plain.getRexBuilder().makeInputRef(expectedInput, 0);
        var expectedValue = plain.getRexBuilder().makeCast(plain.getTypeFactory().createSqlType(org.apache.calcite.sql.type.SqlTypeName.BIGINT), expectedRef);
        var pushed = LogicalProject.create(expectedInput, List.of(), List.of(expectedValue), List.of("casted"));
        var expected = LogicalProject.create(pushed, List.of(), List.of(plain.getRexBuilder().makeInputRef(pushed, 0), plain.literal(true)), List.of("casted", "other"));
        RuleChecks.rewrites(rule, before, expected);

        var nearMiss = LogicalProject.create(input, List.of(), List.of(cast, builder.literal(false)), List.of("casted", "other"));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushAssignmentCastsIntoValues test...");
        runTest();
    }
}
