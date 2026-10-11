package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.JoinReduceExpressions;
import org.qed.RuleBuilder;

import java.util.List;

public class JoinReduceExpressionsTest {

    public static void runTest() {
        var rule = new JoinReduceExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinReduceExpressions rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var rexBuilder = b.getRexBuilder();
        var conjunction = (java.util.function.BiFunction<JoinRelType, Boolean, RelBuilder>) (type, constant) -> {
            b.scan(l).scan(r);
            return b.join(type, rexBuilder.makeCall(SqlStdOperatorTable.AND,
                    RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)), b.literal(constant)));
        };
        var before = conjunction.apply(JoinRelType.INNER, false).build();
        var after = b.scan(l).scan(r).join(JoinRelType.INNER, b.literal(false)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, conjunction.apply(JoinRelType.LEFT, false).build());
        RuleChecks.doesNotApply(rule, conjunction.apply(JoinRelType.INNER, true).build());
    }

    public static void main(String[] args) {
        System.out.println("Running JoinReduceExpressions test...");
        runTest();
    }
}
