package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneGroupByCols;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneGroupByColsTest {

    public static void runTest() {
        var rule = new PruneGroupByCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneGroupByCols rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 4, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(table)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1)),
                        b.aggregateCall(SqlStdOperatorTable.SUM, b.field(2)))
                .project(List.of(b.field(0), b.field(1), b.field(2)), List.of(), true)
                .build();
        var after = b.scan(table)
                .project(List.of(b.field(0), b.field(1), b.field(2)), List.of(), false)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1)),
                        b.aggregateCall(SqlStdOperatorTable.SUM, b.field(2)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(table)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1)),
                        b.aggregateCall(SqlStdOperatorTable.SUM, b.field(2)))
                .project(List.of(b.field(0), b.field(2), b.field(1)), List.of(), true)
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneGroupByCols test...");
        runTest();
    }
}
