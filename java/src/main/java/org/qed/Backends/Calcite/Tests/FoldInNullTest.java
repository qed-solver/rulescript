package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.qed.RRuleInstances.FoldInNull;
import org.qed.RuleBuilder;

public final class FoldInNullTest {
    public static void runTest() {
        var rule = new FoldInNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltNullableMatch(rule);
        structurallyDifferentControl();
    }

    private static void handBuiltNullableMatch(FoldInNull rule) {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, true);
        var builder = RuleChecks.ruleBuilder(qed);
        var input = builder.scan(table).build();
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.EQUALS,
                builder.getRexBuilder().makeInputRef(input.getRowType().getFieldList().get(0).getType(), 0),
                builder.getRexBuilder().makeNullLiteral(builder.getTypeFactory().createSqlType(SqlTypeName.INTEGER)));
        RelNode before = LogicalPlans.filter(input, condition);
        var expectedQed = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedQed, 1, false, true);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedQed);
        var expectedInput = expectedBuilder.scan(expectedTable).build();
        RelNode expected = LogicalPlans.filter(expectedInput,
                expectedBuilder.getRexBuilder().makeNullLiteral(
                        expectedBuilder.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN)));
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void structurallyDifferentControl() {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, true);
        var builder = RuleChecks.ruleBuilder(qed);
        var input = builder.scan(table).build();
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.NOT_EQUALS,
                builder.getRexBuilder().makeInputRef(input.getRowType().getFieldList().get(0).getType(), 0),
                builder.getRexBuilder().makeNullLiteral(builder.getTypeFactory().createSqlType(SqlTypeName.INTEGER)));
        RelNode plan = LogicalPlans.filter(input, condition);
        RuleChecks.doesNotApply(new FoldInNull(), plan);
    }

    private static final class LogicalPlans {
        private static RelNode filter(RelNode input, org.apache.calcite.rex.RexNode condition) {
            return LogicalFilter.create(input, condition);
        }
    }

    public static void main(String[] args) {
        System.out.println("Running FoldInNull test...");
        runTest();
    }
}
