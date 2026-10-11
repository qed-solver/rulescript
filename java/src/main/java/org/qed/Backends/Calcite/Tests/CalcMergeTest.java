package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.CalcMerge;
import org.qed.RuleBuilder;

import java.util.List;

public class CalcMergeTest {

    public static void runTest() {
        var rule = new CalcMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(CalcMerge rule) {
        var base = RuleBuilder.create();
        var s = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var stacked = (java.util.function.Supplier<RelBuilder>) () -> {
            b.scan(s);
            var x = b.field(0);
            var y = b.field(1);
            b.filter(RuleChecks.gt(b, x, 5));
            b.project(b.call(SqlStdOperatorTable.PLUS, x, y), b.call(SqlStdOperatorTable.MINUS, x, y));
            return b.filter(b.call(SqlStdOperatorTable.GREATER_THAN, b.field(0), b.field(1)));
        };
        var before = stacked.get().project(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.field(1)),
                b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1))).build();

        b.scan(s);
        var x = b.field(0);
        var y = b.field(1);
        var sum = b.call(SqlStdOperatorTable.PLUS, x, y);
        var diff = b.call(SqlStdOperatorTable.MINUS, x, y);
        var after = b.filter(b.and(RuleChecks.gt(b, x, 5), b.call(SqlStdOperatorTable.GREATER_THAN, sum, diff)))
                .project(b.call(SqlStdOperatorTable.MULTIPLY, sum, diff), b.call(SqlStdOperatorTable.PLUS, sum,
                        b.literal(1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, stacked.get()
                .project(b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.field(1))).build());
        RuleChecks.doesNotApply(rule, stacked.get().build());
    }

    public static void main(String[] args) {
        System.out.println("Running CalcMerge test...");
        runTest();
    }
}
