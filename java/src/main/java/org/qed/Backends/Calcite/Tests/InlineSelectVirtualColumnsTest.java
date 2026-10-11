package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.InlineSelectVirtualColumns;
import org.qed.RuleBuilder;

public class InlineSelectVirtualColumnsTest {

    public static void runTest() {
        var rule = new InlineSelectVirtualColumns();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(InlineSelectVirtualColumns rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var before = builder.scan(table)
                .project(builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.field(1)),
                        builder.call(SqlStdOperatorTable.MULTIPLY, builder.field(0), builder.field(1)),
                        builder.field(0), builder.field(1))
                .filter(builder.and(RuleChecks.gt(builder, builder.field(0), 5),
                        RuleChecks.gt(builder, builder.field(1), 10)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(RuleChecks.gt(plain, plain.call(SqlStdOperatorTable.PLUS, plain.field(0), plain.field(1)), 5))
                .project(plain.call(SqlStdOperatorTable.PLUS, plain.field(0), plain.field(1)),
                        plain.call(SqlStdOperatorTable.MULTIPLY, plain.field(0), plain.field(1)),
                        plain.field(0), plain.field(1))
                .filter(RuleChecks.gt(plain, plain.field(1), 10))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var singleConjunct = builder.scan(table)
                .project(builder.call(SqlStdOperatorTable.PLUS, builder.field(0), builder.field(1)),
                        builder.call(SqlStdOperatorTable.MULTIPLY, builder.field(0), builder.field(1)),
                        builder.field(0), builder.field(1))
                .filter(RuleChecks.gt(builder, builder.field(0), 5))
                .build();
        RuleChecks.doesNotApply(rule, singleConjunct);
    }

    public static void main(String[] args) {
        System.out.println("Running InlineSelectVirtualColumns test...");
        runTest();
    }
}
