package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RuleBuilder;

public class JoinExtractFilterTest {
    public static void runTest() {
        var rule = new org.qed.RRuleInstances.JoinExtractFilter();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(org.qed.RRuleInstances.JoinExtractFilter rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false, false);
        var r = RuleChecks.table(builder, 1, false, false);
        var left = builder.scan(l);
        var right = builder.scan(r);
        var condition = builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0));
        var plan = left.join(JoinRelType.INNER, condition).build();

        var plain = RuleChecks.ruleBuilder(builder);
        var expectedLeft = plain.scan(l);
        var expectedRight = plain.scan(r);
        var expected = expectedLeft.join(JoinRelType.INNER, plain.literal(true))
                .filter(plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, plan, expected);

        var leftJoinBuilder = RuleBuilder.create();
        var leftTable = RuleChecks.table(leftJoinBuilder, 1, false, false);
        var rightTable = RuleChecks.table(leftJoinBuilder, 1, false, false);
        var leftJoin = leftJoinBuilder.scan(leftTable).scan(rightTable)
                .join(JoinRelType.LEFT, leftJoinBuilder.call(SqlStdOperatorTable.EQUALS,
                        leftJoinBuilder.field(2, 0, 0), leftJoinBuilder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, leftJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
