package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyTrueAnd;
import org.qed.RuleBuilder;

public class SimplifyTrueAndTest {

    public static void runTest() {
        var rule = new SimplifyTrueAnd();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        dropsLeadingTrueConjunct(rule);
    }

    private static void dropsLeadingTrueConjunct(SimplifyTrueAnd rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var scanned = builder.scan(table);
        var predicate = RuleChecks.gt(builder, builder.field(0), 5);
        var before = LogicalFilter.create(scanned.build(),
                builder.call(SqlStdOperatorTable.AND, builder.literal(true), predicate));
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(RuleChecks.gt(plain, plain.field(0), 5))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var rescanned = builder.scan(table);
        var first = RuleChecks.gt(builder, builder.field(0), 5);
        var trailingTrue = LogicalFilter.create(rescanned.build(),
                builder.call(SqlStdOperatorTable.AND, first, builder.literal(true)));
        RuleChecks.doesNotApply(rule, trailingTrue);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyTrueAnd test...");
        runTest();
    }
}
