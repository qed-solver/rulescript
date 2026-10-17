package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRule;
import org.qed.RRuleInstances.FilterTableFunctionTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public final class FilterTableFunctionTransposeTest {
    private FilterTableFunctionTransposeTest() {}

    public static void runTest() {
        RRule rule = new FilterTableFunctionTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltTranspose();
        handBuiltRejectsLeftJoin();
    }

    private static void handBuiltRejectsLeftJoin() {
        var rb = RuleBuilder.create();
        var b = RuleChecks.ruleBuilder(rb);
        var sourceName = RuleChecks.table(rb, 2, false);
        var witnessName = RuleChecks.table(rb, 1, false);
        var source = b.scan(sourceName).build();
        var witness = b.scan(witnessName).build();
        b.push(source).push(witness);
        var cond = b.call(SqlStdOperatorTable.EQUALS,
                b.getRexBuilder().makeInputRef(source.getRowType().getFieldList().get(0).getType(), 0),
                b.getRexBuilder().makeInputRef(witness.getRowType().getFieldList().get(0).getType(), 2));
        var join = b.join(JoinRelType.LEFT, cond).build();
        b.push(join);
        var projected = b.project(List.of(b.field(0), b.field(1)), List.of(), true).build();
        var before = LogicalFilter.create(projected, gt(b, ref(b, projected, 1), 7));
        RuleChecks.doesNotApply(new FilterTableFunctionTranspose(), before);
    }

    private static void handBuiltTranspose() {
        var rb = RuleBuilder.create();
        var b = RuleChecks.ruleBuilder(rb);
        var sourceName = RuleChecks.table(rb, 2, false);
        var witnessName = RuleChecks.table(rb, 1, false);
        var source = b.scan(sourceName).build();
        var witness = b.scan(witnessName).build();
        b.push(source).push(witness);
        var joinCond = b.call(SqlStdOperatorTable.EQUALS,
                b.getRexBuilder().makeInputRef(source.getRowType().getFieldList().get(0).getType(), 0),
                b.getRexBuilder().makeInputRef(witness.getRowType().getFieldList().get(0).getType(), 2));
        var joined = b.join(JoinRelType.INNER, joinCond).build();
        b.push(joined);
        var projection = List.of(b.field(0), b.field(1));
        var projected = b.project(projection, List.of(), true).build();
        var before = LogicalFilter.create(projected, gt(b, ref(b, projected, 1), 7));
        var expectedSource = LogicalFilter.create(source, gt(b, ref(b, source, 1), 7));
        b.push(expectedSource).push(witness);
        var expectedJoinCond = b.call(SqlStdOperatorTable.EQUALS,
                b.getRexBuilder().makeInputRef(expectedSource.getRowType().getFieldList().get(0).getType(), 0),
                b.getRexBuilder().makeInputRef(witness.getRowType().getFieldList().get(0).getType(), 2));
        b.join(JoinRelType.INNER, expectedJoinCond);
        var expectedProjection = List.of(b.field(0), b.field(1));
        var expected = b.project(expectedProjection, List.of(), true).build();
        RuleChecks.rewrites(new FilterTableFunctionTranspose(), before, expected);
    }

    private static RexNode ref(RelBuilder b, RelNode input, int ordinal) {
        return b.getRexBuilder().makeInputRef(input.getRowType().getFieldList().get(ordinal).getType(), ordinal);
    }

    private static RexNode gt(RelBuilder b, RexNode value, int literal) {
        return b.getRexBuilder().makeCall(SqlStdOperatorTable.GREATER_THAN, value,
                b.getRexBuilder().makeExactLiteral(java.math.BigDecimal.valueOf(literal), value.getType()));
    }

    public static void main(String[] args) {
        System.out.println("Running FilterTableFunctionTranspose test...");
        runTest();
    }
}
