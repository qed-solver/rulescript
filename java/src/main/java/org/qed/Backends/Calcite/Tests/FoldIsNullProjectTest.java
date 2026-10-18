package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldIsNullProject;
import org.qed.RuleBuilder;

public class FoldIsNullProjectTest {
    public static void runTest() {
        var rule = new FoldIsNullProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var inputName = RuleChecks.table(builder, 2, false, false);
        var input = builder.scan(inputName).build();
        var rexBuilder = input.getCluster().getRexBuilder();
        var first = rexBuilder.makeInputRef(input, 0);
        var second = rexBuilder.makeInputRef(input, 1);
        var isNull = rexBuilder.makeCall(SqlStdOperatorTable.IS_NULL, first);
        var before = org.apache.calcite.rel.logical.LogicalProject.create(input, java.util.List.of(),
                java.util.List.of(isNull, second), java.util.List.of("is_null", "passthrough"));
        var expected = org.apache.calcite.rel.logical.LogicalProject.create(input, java.util.List.of(),
                java.util.List.of(rexBuilder.makeLiteral(false), second), java.util.List.of("is_null", "passthrough"));
        RuleChecks.rewrites(rule, before, expected);

        var nullableBuilder = RuleBuilder.create();
        var nullableName = RuleChecks.table(nullableBuilder, 2, false, true);
        var nullableInput = nullableBuilder.scan(nullableName).build();
        var nullableRexBuilder = nullableInput.getCluster().getRexBuilder();
        var nullableIsNull = nullableRexBuilder.makeCall(SqlStdOperatorTable.IS_NULL,
                nullableRexBuilder.makeInputRef(nullableInput, 0));
        var nullablePass = nullableRexBuilder.makeInputRef(nullableInput, 1);
        var nullableProject = org.apache.calcite.rel.logical.LogicalProject.create(nullableInput, java.util.List.of(),
                java.util.List.of(nullableIsNull, nullablePass), java.util.List.of("is_null", "passthrough"));
        RuleChecks.doesNotApply(rule, nullableProject);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldIsNullProject test...");
        runTest();
    }
}
