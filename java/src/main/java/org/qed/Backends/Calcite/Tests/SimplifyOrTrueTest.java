package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyOrTrue;
import org.qed.RuleBuilder;

public class SimplifyOrTrueTest {

    public static void runTest() {
        var rule = new SimplifyOrTrue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTable(rule);
    }

    private static void ordinaryTable(SimplifyOrTrue rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var scanned = builder.scan(table);
        var predicate = RuleChecks.gt(builder, builder.field(0), 5);
        var before = LogicalFilter.create(scanned.build(),
                builder.call(SqlStdOperatorTable.OR, predicate, builder.literal(true)));
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.literal(true))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var rescanned = builder.scan(table);
        var first = RuleChecks.gt(builder, builder.field(0), 5);
        var second = RuleChecks.gt(builder, builder.field(0), 10);
        var noTrue = LogicalFilter.create(rescanned.build(),
                builder.call(SqlStdOperatorTable.OR, first, second));
        RuleChecks.doesNotApply(rule, noTrue);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyOrTrue test...");
        runTest();
    }
}
