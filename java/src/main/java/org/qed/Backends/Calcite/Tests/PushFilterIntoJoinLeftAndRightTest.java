package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushFilterIntoJoinLeftAndRight;
import org.qed.RuleBuilder;

public class PushFilterIntoJoinLeftAndRightTest {

    public static void runTest() {
        var rule = new PushFilterIntoJoinLeftAndRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushFilterIntoJoinLeftAndRight rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 2, false, false);
        var r = RuleChecks.table(builder, 2, false, false);
        var before = builder.scan(l).scan(r).join(JoinRelType.INNER, builder.and(
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 1), builder.field(2, 1, 1)),
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 1))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(l)
                .filter(plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), plain.field(1)))
                .scan(r)
                .filter(plain.call(SqlStdOperatorTable.EQUALS, plain.field(0), plain.field(1)))
                .join(JoinRelType.INNER, plain.and(
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)),
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 1), plain.field(2, 1, 1))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var twoConditions = builder.scan(l).scan(r).join(JoinRelType.INNER, builder.and(
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 1), builder.field(2, 1, 1))))
                .build();
        RuleChecks.doesNotApply(rule, twoConditions);
    }

    public static void main(String[] args) {
        System.out.println("Running PushFilterIntoJoinLeftAndRight test...");
        runTest();
    }
}
