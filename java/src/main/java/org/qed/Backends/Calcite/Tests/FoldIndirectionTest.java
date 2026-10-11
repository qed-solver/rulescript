package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldIndirection;
import org.qed.RuleBuilder;

import java.util.List;

public class FoldIndirectionTest {

    public static void runTest() {
        var rule = new FoldIndirection();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldIndirection rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var one = b.literal(1);
        var two = b.literal(2);
        var before = b.scan(table)
                .project(List.of(b.call(SqlStdOperatorTable.PLUS, b.field(0), one),
                        b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), two)), List.of(), true)
                .project(List.of(b.field(1)), List.of(), true)
                .build();
        var after = b.scan(table)
                .project(List.of(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), two)), List.of(), true)
                .build();
        RuleChecks.rewrites(rule, before, after);

        var first = b.scan(table)
                .project(List.of(b.call(SqlStdOperatorTable.PLUS, b.field(0), one),
                        b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), two)), List.of(), true)
                .project(List.of(b.field(0)), List.of(), true)
                .build();
        RuleChecks.doesNotApply(rule, first);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldIndirection test...");
        runTest();
    }
}
