package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.SimplifySameVarEqualities;
import org.qed.RuleBuilder;

public class SimplifySameVarEqualitiesTest {

    public static void runTest() {
        var rule = new SimplifySameVarEqualities();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SimplifySameVarEqualities rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var nullInt = plain.getRexBuilder().makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var after = plain.scan(table)
                .filter(plain.or(
                        plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(0)),
                        nullInt))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.literal(5)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifySameVarEqualities test...");
        runTest();
    }
}
