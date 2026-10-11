package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.ConvertGroupByToDistinct;
import org.qed.RuleBuilder;

public class ConvertGroupByToDistinctTest {

    public static void runTest() {
        var rule = new ConvertGroupByToDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ConvertGroupByToDistinct rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 2, false, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).aggregate(b.groupKey(0, 1)).build();
        var after = b.scan(t).scan(t).intersect(false, 2).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).aggregate(b.groupKey(0)).build());
        RuleChecks.doesNotApply(rule, b.scan(t)
                .aggregate(b.groupKey(0, 1), b.aggregateCall(SqlStdOperatorTable.COUNT, b.field(0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ConvertGroupByToDistinct test...");
        runTest();
    }
}
