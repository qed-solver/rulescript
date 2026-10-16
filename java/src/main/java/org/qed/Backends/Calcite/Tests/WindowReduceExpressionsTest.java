package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.WindowReduceExpressions;
import org.qed.RuleBuilder;

public class WindowReduceExpressionsTest {

    public static void runTest() {
        var rule = new WindowReduceExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(WindowReduceExpressions rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var input = b.scan(t).build();
        var window = (java.util.function.Function<RexNode, RelNode>) operand -> {
            var perPart = b.push(input).project(b.field(0), operand).aggregate(b.groupKey(0),
                    b.aggregateCall(SqlStdOperatorTable.COUNT, b.field(1))).build();
            return b.push(input).push(perPart)
                    .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build();
        };
        var folded = (java.util.function.Function<RexNode, RexNode>) value -> {
            b.push(input);
            var and = b.call(SqlStdOperatorTable.AND, value, RuleChecks.gt(b, b.field(1), 0));
            b.build();
            return and;
        };
        var before = window.apply(folded.apply(b.literal(false)));
        var after = window.apply(b.literal(false));
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, window.apply(folded.apply(b.literal(true))));
    }

    public static void main(String[] args) {
        System.out.println("Running WindowReduceExpressions test...");
        runTest();
    }
}
