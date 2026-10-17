package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.PushFilterIntoJoinRight;
import org.qed.RuleBuilder;

public class PushFilterIntoJoinRightTest {
    public static void runTest() {
        var rule = new PushFilterIntoJoinRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushFilterIntoJoinRight rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false, false);
        var r = RuleChecks.table(builder, 1, false, false);
        var leftRef = builder.scan(l).scan(r).field(2, 0, 0);
        var rightRef = builder.field(2, 1, 0);
        var f = builder.call(SqlStdOperatorTable.GREATER_THAN, rightRef, builder.literal(0));
        var g = builder.call(SqlStdOperatorTable.LESS_THAN, leftRef, rightRef);
        var plan = builder.join(JoinRelType.INNER, builder.and(f, g)).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(l).scan(r)
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(1, 0, 0), plain.literal(0)))
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.LESS_THAN,
                        plain.field(2, 0, 0), plain.field(2, 1, 0))).build();
        RuleChecks.rewrites(rule, plan, expected);

        var missBuilder = RuleBuilder.create();
        var ml = RuleChecks.table(missBuilder, 1, false, false);
        var mr = RuleChecks.table(missBuilder, 1, false, false);
        var miss = missBuilder.scan(ml).scan(mr).join(JoinRelType.LEFT,
                missBuilder.and(missBuilder.call(SqlStdOperatorTable.GREATER_THAN,
                                missBuilder.field(2, 1, 0), missBuilder.literal(0)),
                        missBuilder.call(SqlStdOperatorTable.LESS_THAN,
                                missBuilder.field(2, 0, 0), missBuilder.field(2, 1, 0)))).build();
        RuleChecks.doesNotApply(rule, miss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushFilterIntoJoinRight test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            throw new AssertionError(CalciteTester.failures() + " test failures");
        }
    }
}
