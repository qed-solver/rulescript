package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRule;
import org.qed.RRuleInstances.FoldFunctionWithNullArg;
import org.qed.RuleBuilder;

public class FoldFunctionWithNullArgTest {
    public static void runTest() {
        var rule = new FoldFunctionWithNullArg();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesIndependentPlan(rule);
        rejectsNonNullArgument(rule);
    }

    private static void rewritesIndependentPlan(RRule rule) {
        var inputBuilder = RuleBuilder.create();
        var table = RuleChecks.table(inputBuilder, 1, false, true);
        RelNode input = inputBuilder.scan(table).build();
        var rexBuilder = input.getCluster().getRexBuilder();
        var inputValue = rexBuilder.makeInputRef(input, 0);
        var nullValue = rexBuilder.makeNullLiteral(input.getRowType().getFieldList().get(0).getType());
        var equalsNull = rexBuilder.makeCall(SqlStdOperatorTable.EQUALS, inputValue, nullValue);
        RelNode actualInput = org.apache.calcite.rel.logical.LogicalProject.create(input, java.util.List.of(),
                java.util.List.of(equalsNull), java.util.List.of("$f0"));

        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false, true);
        RelNode expectedInput = expectedBuilder.scan(expectedTable).build();
        var expectedRexBuilder = expectedInput.getCluster().getRexBuilder();
        var expectedNull = expectedRexBuilder.makeNullLiteral(expectedRexBuilder.getTypeFactory()
                .createSqlType(org.apache.calcite.sql.type.SqlTypeName.BOOLEAN));
        RelNode expected = org.apache.calcite.rel.logical.LogicalProject.create(expectedInput, java.util.List.of(),
                java.util.List.of(expectedNull), java.util.List.of("$f0"));
        RuleChecks.rewrites(rule, actualInput, expected);
    }

    private static void rejectsNonNullArgument(RRule rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, true);
        builder.scan(table);
        var value = builder.field(0);
        var condition = builder.call(SqlStdOperatorTable.EQUALS, value, builder.literal(7));
        builder.project(java.util.List.of(condition), java.util.List.of("$f0"), true);
        RuleChecks.doesNotApply(rule, builder.build());
    }

    public static void main(String[] args) {
        System.out.println("Running FoldFunctionWithNullArg test...");
        runTest();
    }
}
