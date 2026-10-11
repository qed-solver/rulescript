package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyAndFalse;
import org.qed.RuleBuilder;

public class SimplifyAndFalseTest {

    public static void runTest() {
        var rule = new SimplifyAndFalse();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTable(rule);
    }

    private static void ordinaryTable(SimplifyAndFalse rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        b.scan(table);
        var predicate = RuleChecks.gt(b, b.field(0), 5);
        var conjunction = b.call(SqlStdOperatorTable.AND, predicate, b.literal(false));
        var before = LogicalFilter.create(b.build(), conjunction);
        var after = b.scan(table).filter(b.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = b.scan(table)
                .filter(b.call(SqlStdOperatorTable.AND, RuleChecks.gt(b, b.field(0), 5),
                        RuleChecks.gt(b, b.field(0), 10)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyAndFalse test...");
        runTest();
    }
}
