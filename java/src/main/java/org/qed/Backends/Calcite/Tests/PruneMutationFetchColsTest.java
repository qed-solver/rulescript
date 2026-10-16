package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneMutationFetchCols;
import org.qed.RuleBuilder;

public class PruneMutationFetchColsTest {

    public static void runTest() {
        var rule = new PruneMutationFetchCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneMutationFetchCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0))).build();
        var after = b.scan(t).project(b.field(0)).project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(1))).build());
        RuleChecks.doesNotApply(rule, b.scan(t)
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0)), b.field(1)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneMutationFetchCols test...");
        runTest();
    }
}
