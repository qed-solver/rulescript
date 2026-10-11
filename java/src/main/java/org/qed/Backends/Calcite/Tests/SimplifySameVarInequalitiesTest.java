package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.SimplifySameVarInequalities;
import org.qed.RuleBuilder;

public class SimplifySameVarInequalitiesTest {

    public static void runTest() {
        var rule = new SimplifySameVarInequalities();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTable(rule);
    }

    private static void ordinaryTable(SimplifySameVarInequalities rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var plain = RuleChecks.ruleBuilder(builder);
        var before = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.NOT_EQUALS, plain.field(0), plain.field(0)))
                .build();
        var nullInt = plain.getRexBuilder().makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var after = plain.scan(table).filter(plain.and(
                        plain.call(SqlStdOperatorTable.IS_NULL, plain.field(0)),
                        nullInt))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT_EQUALS, builder.field(0), five))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifySameVarInequalities test...");
        runTest();
    }
}
