package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.NullDownPropagation;
import org.qed.RuleBuilder;

public class NullDownPropagationTest {
    public static void runTest() {
        var rule = new NullDownPropagation();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(NullDownPropagation rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var source = builder.scan(table);
        var comparison = builder.call(SqlStdOperatorTable.LESS_THAN,
                builder.field(0), builder.field(1));
        var plan = org.apache.calcite.rel.logical.LogicalFilter.create(source.build(),
                builder.call(SqlStdOperatorTable.IS_NULL, comparison));

        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(table).filter(plain.call(SqlStdOperatorTable.OR,
                plain.call(SqlStdOperatorTable.IS_NULL, plain.field(0)),
                plain.call(SqlStdOperatorTable.IS_NULL, plain.field(1)))).build();
        RuleChecks.rewrites(rule, plan, expected);

        var differentComparison = builder.scan(table).filter(builder.call(SqlStdOperatorTable.IS_NULL,
                builder.call(SqlStdOperatorTable.GREATER_THAN,
                        builder.field(0), builder.field(1)))).build();
        RuleChecks.doesNotApply(rule, differentComparison);
    }

    public static void main(String[] args) {
        System.out.println("Running NullDownPropagation test...");
        runTest();
    }
}
