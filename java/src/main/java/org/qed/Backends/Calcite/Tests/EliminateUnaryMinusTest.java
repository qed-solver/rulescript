package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.EliminateUnaryMinus;
import org.qed.RuleBuilder;

public class EliminateUnaryMinusTest {

    public static void runTest() {
        var rule = new EliminateUnaryMinus();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateUnaryMinus rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t)
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0))))
                .build();
        var after = b.scan(t).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t).project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.field(0))).build());
        RuleChecks.doesNotApply(rule, b.scan(t)
                .project(b.call(SqlStdOperatorTable.UNARY_MINUS, b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1))))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateUnaryMinus test...");
        runTest();
    }
}
