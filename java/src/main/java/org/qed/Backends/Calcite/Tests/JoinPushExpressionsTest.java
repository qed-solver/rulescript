package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinPushExpressions;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinPushExpressionsTest {

    public static void runTest() {
        var rule = new JoinPushExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinPushExpressions rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var join = (java.util.function.BiFunction<JoinRelType, Boolean, RelBuilder>) (type, residual) -> {
            b.scan(l).scan(r);
            var equal = b.call(SqlStdOperatorTable.EQUALS, b.call(SqlStdOperatorTable.PLUS, b.field(2, 0, 0),
                    b.literal(1)), b.call(SqlStdOperatorTable.MULTIPLY, b.field(2, 1, 0), b.literal(2)));
            var less = b.call(SqlStdOperatorTable.LESS_THAN, b.field(2, 0, 0), b.field(2, 1, 0));
            return b.join(type, residual ? b.and(equal, less) : equal);
        };
        var before = join.apply(JoinRelType.INNER, true).build();

        b.scan(l).project(b.field(0), b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)));
        b.scan(r).project(b.field(0), b.call(SqlStdOperatorTable.MULTIPLY, b.field(0), b.literal(2)));
        b.join(JoinRelType.INNER, b.and(RuleChecks.eq(b, b.field(2, 0, 1), b.field(2, 1, 1)),
                b.call(SqlStdOperatorTable.LESS_THAN, b.field(2, 0, 0), b.field(2, 1, 0))));
        var after = b.project(b.field(0), b.field(2)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, join.apply(JoinRelType.LEFT, true).build());
        RuleChecks.doesNotApply(rule, join.apply(JoinRelType.INNER, false).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinPushExpressions test...");
        runTest();
    }
}
