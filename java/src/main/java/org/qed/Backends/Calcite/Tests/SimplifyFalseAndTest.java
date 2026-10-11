package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyFalseAnd;
import org.qed.RuleBuilder;

public class SimplifyFalseAndTest {

    public static void runTest() {
        var rule = new SimplifyFalseAnd();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        dropsFalseConjunct(rule);
    }

    private static void dropsFalseConjunct(SimplifyFalseAnd rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var scanned = builder.scan(table);
        var predicate = RuleChecks.gt(builder, builder.field(0), 5);
        var before = LogicalFilter.create(scanned.build(),
                builder.call(SqlStdOperatorTable.AND, builder.literal(false), predicate));
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.literal(false))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var rescanned = builder.scan(table);
        var first = RuleChecks.gt(builder, builder.field(0), 5);
        var second = RuleChecks.gt(builder, builder.field(0), 10);
        var noFalse = LogicalFilter.create(rescanned.build(),
                builder.call(SqlStdOperatorTable.AND, first, second));
        RuleChecks.doesNotApply(rule, noFalse);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyFalseAnd test...");
        runTest();
    }
}
