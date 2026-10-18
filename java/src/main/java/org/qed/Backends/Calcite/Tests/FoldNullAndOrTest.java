package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldNullAndOr;
import org.qed.RuleBuilder;

public final class FoldNullAndOrTest {
    public static void runTest() {
        var rule = new FoldNullAndOr();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltAndNulls();
        handBuiltMixedAndDoesNotMatch();
        handBuiltOrNullsDoNotMatch();
    }

    private static void handBuiltAndNulls() {
        var b = RuleBuilder.create();
        var table = RuleChecks.table(b, 1, false);
        var input = b.scan(table).build();
        var rb = b.getRexBuilder();
        var null1 = rb.makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var null2 = rb.makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var condition = rb.makeCall(SqlStdOperatorTable.AND, null1, null2);
        var before = org.apache.calcite.rel.logical.LogicalFilter.create(input, condition);
        var expectedCondition = rb.makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var expected = org.apache.calcite.rel.logical.LogicalFilter.create(input, expectedCondition);
        RuleChecks.rewrites(new FoldNullAndOr(), before, expected);
    }

    private static void handBuiltMixedAndDoesNotMatch() {
        var b = RuleBuilder.create();
        var table = RuleChecks.table(b, 1, false);
        var input = b.scan(table).build();
        var rb = b.getRexBuilder();
        var nullValue = rb.makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var trueValue = rb.makeLiteral(true);
        var condition = rb.makeCall(SqlStdOperatorTable.AND, nullValue, trueValue);
        RuleChecks.doesNotApply(new FoldNullAndOr(),
                org.apache.calcite.rel.logical.LogicalFilter.create(input, condition));
    }

    private static void handBuiltOrNullsDoNotMatch() {
        var b = RuleBuilder.create();
        var table = RuleChecks.table(b, 1, false);
        var input = b.scan(table).build();
        var rb = b.getRexBuilder();
        var null1 = rb.makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var null2 = rb.makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var condition = rb.makeCall(SqlStdOperatorTable.OR, null1, null2);
        RuleChecks.doesNotApply(new FoldNullAndOr(),
                org.apache.calcite.rel.logical.LogicalFilter.create(input, condition));
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullAndOr test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError("FoldNullAndOr test failed");
        }
    }
}
