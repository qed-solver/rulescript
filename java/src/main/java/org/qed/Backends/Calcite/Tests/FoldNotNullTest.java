package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNotNull;
import org.qed.RuleBuilder;

public class FoldNotNullTest {

    public static void runTest() {
        var rule = new FoldNotNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldNotNull rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var nullBool = b.getRexBuilder().makeNullLiteral(b.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var before = b.scan(table)
                .filter(b.call(SqlStdOperatorTable.NOT, nullBool))
                .build();
        var after = b.scan(table).filter(nullBool).build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = b.scan(table)
                .filter(b.call(SqlStdOperatorTable.NOT, RuleChecks.gt(b, b.field(0), 5)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNotNull test...");
        runTest();
    }
}
