package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.CalcReduceExpressions;
import org.qed.RuleBuilder;

import java.util.List;

public class CalcReduceExpressionsTest {

    public static void runTest() {
        var rule = new CalcReduceExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(CalcReduceExpressions rule) {
        var base = RuleBuilder.create();
        var s = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var neverTrue = (java.util.function.Function<RexNode, RelBuilder>) cond -> {
            b.push(org.apache.calcite.rel.logical.LogicalFilter.create(b.scan(s).build(), cond));
            return b;
        };
        var before = neverTrue.apply(b.literal(false))
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)), b.field(0)).build();
        var after = b.scan(s).project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)), b.field(0))
                .empty().build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, neverTrue.apply(RuleChecks.gt(b, b.scan(s).field(0), 5))
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.field(1)), b.field(0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running CalcReduceExpressions test...");
        runTest();
    }
}
