package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldIndirection;
import org.qed.RuleBuilder;

import java.util.List;

public final class FoldIndirectionTest {
    public static void runTest() {
        var rule = new FoldIndirection();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsDifferentIndex();
    }

    private static void rewritesHandBuiltPlan() {
        var input = base();
        var builder = builder(input);
        var source = builder.scan(input.table()).build();
        var fields = source.getRowType().getFieldList();
        var rex = builder.getRexBuilder();
        RexNode first = rex.makeCall(SqlStdOperatorTable.PLUS, rex.makeInputRef(fields.get(0).getType(), 0),
                rex.makeExactLiteral(java.math.BigDecimal.ONE));
        RexNode second = rex.makeCall(SqlStdOperatorTable.PLUS, rex.makeInputRef(fields.get(0).getType(), 0),
                rex.makeExactLiteral(java.math.BigDecimal.valueOf(2)));
        builder.push(org.apache.calcite.rel.logical.LogicalProject.create(source, List.of(), List.of(first, second),
                List.of("first", "second")));
        var array = builder.build();
        builder.push(org.apache.calcite.rel.logical.LogicalProject.create(array, List.of(),
                List.of(rex.makeInputRef(array.getRowType().getFieldList().get(1).getType(), 1)), List.of("selected")));
        var before = builder.build();

        var expectedBuilder = builder(input);
        var expectedSource = expectedBuilder.scan(input.table()).build();
        var expectedFields = expectedSource.getRowType().getFieldList();
        var expectedRex = expectedBuilder.getRexBuilder();
        var expectedExpr = expectedRex.makeCall(SqlStdOperatorTable.PLUS,
                expectedRex.makeInputRef(expectedFields.get(0).getType(), 0),
                expectedRex.makeExactLiteral(java.math.BigDecimal.valueOf(2)));
        expectedBuilder.push(org.apache.calcite.rel.logical.LogicalProject.create(expectedSource, List.of(),
                List.of(expectedExpr), List.of("selected")));
        var expected = expectedBuilder.build();
        RuleChecks.rewrites(rule(), before, expected);
    }

    private static void rejectsDifferentIndex() {
        var input = base();
        var builder = builder(input);
        var source = builder.scan(input.table()).build();
        var fields = source.getRowType().getFieldList();
        var rex = builder.getRexBuilder();
        RexNode first = rex.makeCall(SqlStdOperatorTable.PLUS, rex.makeInputRef(fields.get(0).getType(), 0),
                rex.makeExactLiteral(java.math.BigDecimal.ONE));
        RexNode second = rex.makeCall(SqlStdOperatorTable.PLUS, rex.makeInputRef(fields.get(0).getType(), 0),
                rex.makeExactLiteral(java.math.BigDecimal.valueOf(2)));
        builder.push(org.apache.calcite.rel.logical.LogicalProject.create(source, List.of(), List.of(first, second),
                List.of("first", "second")));
        var array = builder.build();
        builder.push(org.apache.calcite.rel.logical.LogicalProject.create(array, List.of(),
                List.of(rex.makeInputRef(array.getRowType().getFieldList().get(0).getType(), 0)), List.of("selected")));
        RuleChecks.doesNotApply(rule(), builder.build());
    }

    private static org.qed.RRule rule() {
        return new FoldIndirection();
    }

    private static Fixture base() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        return new Fixture(builder, table);
    }

    private static org.apache.calcite.tools.RelBuilder builder(Fixture fixture) {
        return RuleChecks.ruleBuilder(fixture.builder());
    }

    private record Fixture(RuleBuilder builder, String table) {}

    public static void main(String[] args) {
        System.out.println("Running FoldIndirection test...");
        runTest();
    }
}
