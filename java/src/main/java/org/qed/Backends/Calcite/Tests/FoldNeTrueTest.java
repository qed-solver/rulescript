package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNeTrue;
import org.qed.RuleBuilder;

public class FoldNeTrueTest {

    public static void runTest() {
        var rule = new FoldNeTrue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        notEqualsTrue(rule);
    }

    private static void notEqualsTrue(FoldNeTrue rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var scanned = builder.scan(table);
        var predicate = RuleChecks.gt(builder, builder.field(0), 5);
        var before = LogicalFilter.create(scanned.build(),
                builder.call(SqlStdOperatorTable.NOT_EQUALS, predicate, builder.literal(true)));
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(plain.not(RuleChecks.gt(plain, plain.field(0), 5)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var rescanned = builder.scan(table);
        var falsePredicate = RuleChecks.gt(builder, builder.field(0), 5);
        var mismatched = LogicalFilter.create(rescanned.build(),
                builder.call(SqlStdOperatorTable.NOT_EQUALS, falsePredicate, builder.literal(false)));
        RuleChecks.doesNotApply(rule, mismatched);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNeTrue test...");
        runTest();
    }
}
