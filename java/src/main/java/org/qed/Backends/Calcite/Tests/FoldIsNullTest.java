package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldIsNull;
import org.qed.RuleBuilder;

public class FoldIsNullTest {
    public static void runTest() {
        var rule = new FoldIsNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var source = builder.scan(table).build();
        var matched = LogicalFilter.create(source, builder.getRexBuilder().makeCall(
                SqlStdOperatorTable.IS_NULL,
                builder.getRexBuilder().makeNullLiteral(builder.getTypeFactory().createSqlType(SqlTypeName.INTEGER))));
        var expected = builder.push(source).filter(builder.literal(true)).build();
        RuleChecks.rewrites(rule, matched, expected);

        var otherType = builder.getRexBuilder().makeNullLiteral(
                builder.getTypeFactory().createSqlType(SqlTypeName.VARCHAR));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(source,
                builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NULL, otherType)));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(source,
                builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                        builder.getRexBuilder().makeNullLiteral(
                                builder.getTypeFactory().createSqlType(SqlTypeName.INTEGER)))));
    }

    public static void main(String[] args) {
        System.out.println("Running FoldIsNull test...");
        runTest();
    }
}
