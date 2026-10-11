package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldIsNull;
import org.qed.RuleBuilder;

public class FoldIsNullTest {

    public static void runTest() {
        var rule = new FoldIsNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nullLiteral(rule);
    }

    private static void nullLiteral(FoldIsNull rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var nullInt = plain.getRexBuilder()
                .makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var before = LogicalFilter.create(plain.scan(table).build(),
                plain.call(SqlStdOperatorTable.IS_NULL, nullInt));
        var after = plain.scan(table).filter(plain.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        var columnNull = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.IS_NULL, builder.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, columnNull);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldIsNull test...");
        runTest();
    }
}
