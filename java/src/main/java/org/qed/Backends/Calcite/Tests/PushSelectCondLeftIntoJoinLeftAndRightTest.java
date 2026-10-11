package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushSelectCondLeftIntoJoinLeftAndRight;
import org.qed.RuleBuilder;

public class PushSelectCondLeftIntoJoinLeftAndRightTest {

    public static void runTest() {
        var rule = new PushSelectCondLeftIntoJoinLeftAndRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectCondLeftIntoJoinLeftAndRight rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var p = builder.genericPredicateOp("P", true);
        var before = builder.scan(left).scan(right)
                .join(JoinRelType.LEFT, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.call(p, builder.field(0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(left)
                .filter(plain.call(p, plain.field(0)))
                .scan(right)
                .filter(plain.call(p, plain.field(0)))
                .join(JoinRelType.LEFT, plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var inner = builder.scan(left).scan(right)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.call(p, builder.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, inner);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectCondLeftIntoJoinLeftAndRight test...");
        runTest();
    }
}
