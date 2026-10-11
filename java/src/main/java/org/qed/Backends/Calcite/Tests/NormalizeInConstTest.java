package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NormalizeInConst;
import org.qed.RuleBuilder;

public class NormalizeInConstTest {

    public static void runTest() {
        var rule = new NormalizeInConst();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NormalizeInConst rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 3, false);
        var before = builder.scan(table)
                .filter(builder.or(
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1)),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(2)),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1)),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(2))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.or(
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), plain.field(1)),
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), plain.field(2))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var pair = builder.scan(table)
                .filter(builder.or(
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1)),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(2))))
                .build();
        RuleChecks.doesNotApply(rule, pair);
    }

    public static void main(String[] args) {
        System.out.println("Running NormalizeInConst test...");
        runTest();
    }
}
