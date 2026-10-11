package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyInSingleElement;
import org.qed.RuleBuilder;

public class SimplifyInSingleElementTest {

    public static void runTest() {
        var rule = new SimplifyInSingleElement();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        singleElementIn(rule);
    }

    private static void singleElementIn(SimplifyInSingleElement rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.field(1)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var wrongColumn = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), builder.literal(5)))
                .build();
        RuleChecks.doesNotApply(rule, wrongColumn);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyInSingleElement test...");
        runTest();
    }
}
