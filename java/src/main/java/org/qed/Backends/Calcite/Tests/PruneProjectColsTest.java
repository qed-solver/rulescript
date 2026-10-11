package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PruneProjectCols;
import org.qed.RuleBuilder;

public class PruneProjectColsTest {

    public static void runTest() {
        var rule = new PruneProjectCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneProjectCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t)
                .project(b.field(0),
                        b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)),
                        b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)))
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)))
                .build();
        var after = b.scan(t)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0),
                        b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var wideTop = b.scan(t)
                .project(b.field(0),
                        b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)),
                        b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)))
                .project(b.field(0), b.field(1))
                .build();
        RuleChecks.doesNotApply(rule, wideTop);

        var computedPassthrough = b.scan(t)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)),
                        b.field(0),
                        b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)))
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)))
                .build();
        RuleChecks.doesNotApply(rule, computedPassthrough);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneProjectCols test...");
        runTest();
    }
}
