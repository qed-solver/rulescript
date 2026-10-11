package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldIsNotNull;
import org.qed.RuleBuilder;

public class FoldIsNotNullTest {

    public static void runTest() {
        var rule = new FoldIsNotNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldIsNotNull rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var nullInt = b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var before = b.scan(table)
                .filter(b.call(SqlStdOperatorTable.IS_NOT_NULL, nullInt))
                .build();
        var after = b.scan(table).filter(b.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = b.scan(table)
                .filter(b.call(SqlStdOperatorTable.IS_NULL, nullInt))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldIsNotNull test...");
        runTest();
    }
}
