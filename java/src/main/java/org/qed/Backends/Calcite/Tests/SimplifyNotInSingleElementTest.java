package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyNotInSingleElement;
import org.qed.RuleBuilder;

public class SimplifyNotInSingleElementTest {

    public static void runTest() {
        var rule = new SimplifyNotInSingleElement();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        singleElementNotIn(rule);
    }

    private static void singleElementNotIn(SimplifyNotInSingleElement rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT,
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.NOT_EQUALS, plain.field(0), plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var againstLiteral = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.NOT,
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.literal(5))))
                .build();
        RuleChecks.doesNotApply(rule, againstLiteral);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyNotInSingleElement test...");
        runTest();
    }
}
