package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneWithCols;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneWithColsTest {

    public static void runTest() {
        var rule = new PruneWithCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneWithCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).project(List.of(b.field(0)), List.of(), true)
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0)), b.field(0)).build();
        var after = b.scan(t).project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0)), b.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).project(List.of(b.field(0)), List.of(), true)
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneWithCols test...");
        runTest();
    }
}
