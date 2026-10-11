package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNullTupleIsTupleNull;
import org.qed.RuleBuilder;

public class FoldNullTupleIsTupleNullTest {

    public static void runTest() {
        var rule = new FoldNullTupleIsTupleNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nullTuple(rule);
    }

    private static void nullTuple(FoldNullTupleIsTupleNull rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var nullInt = plain.getRexBuilder()
                .makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var isNull = plain.call(SqlStdOperatorTable.IS_NULL, nullInt);
        var before = LogicalFilter.create(plain.scan(table).build(),
                plain.getRexBuilder().makeCall(SqlStdOperatorTable.AND, isNull, isNull));
        var after = plain.scan(table).filter(plain.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        var columnNull = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.AND,
                        builder.call(SqlStdOperatorTable.IS_NULL, builder.field(0)),
                        builder.call(SqlStdOperatorTable.IS_NULL, builder.field(0))))
                .build();
        RuleChecks.doesNotApply(rule, columnNull);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullTupleIsTupleNull test...");
        runTest();
    }
}
