package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ProjectCalcMerge;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectCalcMergeTest {

    public static void runTest() {
        var rule = new ProjectCalcMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectCalcMerge rule) {
        var base = RuleBuilder.create();
        var s = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var calc = (java.util.function.Supplier<RelBuilder>) () -> {
            b.scan(s).filter(RuleChecks.gt(b, b.field(0), 5));
            return b.project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)),
                    b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.field(1)));
        };
        var before = calc.get().project(b.call(SqlStdOperatorTable.MINUS, b.field(0), b.field(1)),
                b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1))).build();

        b.scan(s).filter(RuleChecks.gt(b, b.field(0), 5));
        var sum = b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1));
        var product = b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.field(1));
        var after = b.project(b.call(SqlStdOperatorTable.MINUS, sum, product),
                b.call(SqlStdOperatorTable.PLUS, sum, product)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, calc.get().project(b.call(SqlStdOperatorTable.MINUS, b.field(0), b.field(1)))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(s)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)),
                        b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.field(1)))
                .project(b.call(SqlStdOperatorTable.MINUS, b.field(0), b.field(1)),
                        b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectCalcMerge test...");
        runTest();
    }
}
