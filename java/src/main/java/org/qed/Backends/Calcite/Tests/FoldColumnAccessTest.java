package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldColumnAccess;
import org.qed.RuleBuilder;

import java.util.List;

public class FoldColumnAccessTest {

    public static void runTest() {
        var rule = new FoldColumnAccess();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldColumnAccess rule) {
        var base = RuleBuilder.create();
        var one = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var plusOne = b.literal(1);
        var before = b.scan(one)
                .project(List.of(b.field(0), b.call(SqlStdOperatorTable.PLUS, b.field(0), plusOne)), List.of(), true)
                .project(List.of(b.field(1)), List.of(), true)
                .build();
        var after = b.scan(one)
                .project(List.of(b.call(SqlStdOperatorTable.PLUS, b.field(0), plusOne)), List.of(), true)
                .build();
        RuleChecks.rewrites(rule, before, after);

        var narrow = b.scan(one)
                .project(List.of(b.field(0), b.call(SqlStdOperatorTable.PLUS, b.field(0), plusOne)), List.of(), true)
                .project(List.of(b.field(0)), List.of(), true)
                .build();
        RuleChecks.doesNotApply(rule, narrow);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldColumnAccess test...");
        runTest();
    }
}
