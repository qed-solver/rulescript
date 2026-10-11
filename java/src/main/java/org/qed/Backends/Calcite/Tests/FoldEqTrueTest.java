package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldEqTrue;
import org.qed.RuleBuilder;

public class FoldEqTrueTest {

    public static void runTest() {
        var rule = new FoldEqTrue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        equalsTrue(rule);
    }

    private static void equalsTrue(FoldEqTrue rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var scanned = builder.scan(table);
        var predicate = RuleChecks.gt(builder, builder.field(0), 5);
        var before = LogicalFilter.create(scanned.build(),
                builder.call(SqlStdOperatorTable.EQUALS, predicate, builder.literal(true)));
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .filter(RuleChecks.gt(plain, plain.field(0), 5))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var rescanned = builder.scan(table);
        var falsePredicate = RuleChecks.gt(builder, builder.field(0), 5);
        var mismatched = LogicalFilter.create(rescanned.build(),
                builder.call(SqlStdOperatorTable.EQUALS, falsePredicate, builder.literal(false)));
        RuleChecks.doesNotApply(rule, mismatched);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldEqTrue test...");
        runTest();
    }
}
