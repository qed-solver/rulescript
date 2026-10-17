package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyIsNullCondition;
import org.qed.RuleBuilder;

public final class SimplifyIsNullConditionTest {
    public static void runTest() {
        var rule = new SimplifyIsNullCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesConcreteNotNullColumn();
        rejectsNullableColumn();
    }

    private static void rewritesConcreteNotNullColumn() {
        var builder = RuleBuilder.create();
        var typeFactory = builder.getTypeFactory();
        RelDataType rowType = typeFactory.builder()
                .add("col", typeFactory.createTypeWithNullability(typeFactory.createSqlType(SqlTypeName.INTEGER), false))
                .add("other", typeFactory.createTypeWithNullability(typeFactory.createSqlType(SqlTypeName.INTEGER), true))
                .build();
        RelNode input = inputWithFilters(builder, rowType);
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NULL,
                builder.getRexBuilder().makeInputRef(rowType.getFieldList().get(0).getType(), 0));
        RelNode before = LogicalFilter.create(input, condition);
        var expected = LogicalFilter.create(input, builder.getRexBuilder().makeLiteral(false));
        RuleChecks.rewrites(new SimplifyIsNullCondition(), before, expected);
    }

    private static void rejectsNullableColumn() {
        var builder = RuleBuilder.create();
        var typeFactory = builder.getTypeFactory();
        RelDataType rowType = typeFactory.builder()
                .add("col", typeFactory.createTypeWithNullability(typeFactory.createSqlType(SqlTypeName.INTEGER), true))
                .add("other", typeFactory.createTypeWithNullability(typeFactory.createSqlType(SqlTypeName.INTEGER), true))
                .build();
        RelNode input = inputWithFilters(builder, rowType);
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NULL,
                builder.getRexBuilder().makeInputRef(rowType.getFieldList().get(0).getType(), 0));
        RuleChecks.doesNotApply(new SimplifyIsNullCondition(), LogicalFilter.create(input, condition));
    }

    private static RelNode inputWithFilters(RuleBuilder builder, RelDataType rowType) {
        RelNode input = LogicalValues.create(builder.getCluster(), rowType,
                com.google.common.collect.ImmutableList.of(com.google.common.collect.ImmutableList.of()));
        RexNode trueCondition = builder.getRexBuilder().makeLiteral(true);
        input = LogicalFilter.create(input, trueCondition);
        return LogicalFilter.create(input, trueCondition);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyIsNullCondition test...");
        runTest();
    }
}
