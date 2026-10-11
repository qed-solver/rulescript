package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.InlineJoinConstantsRight;
import org.qed.RuleBuilder;

public class InlineJoinConstantsRightTest {

    public static void runTest() {
        var rule = new InlineJoinConstantsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        constantRightInput(rule);
    }

    private static void constantRightInput(InlineJoinConstantsRight rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var source = RuleChecks.table(builder, 1, false);
        var constant = RuleChecks.table(builder, 1, true, false);
        var before = builder.scan(left)
                .scan(source)
                .scan(constant)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(left)
                .scan(source)
                .scan(constant)
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nonUnique = RuleChecks.table(builder, 1, false, false);
        var nearMiss = builder.scan(left)
                .scan(source)
                .scan(nonUnique)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running InlineJoinConstantsRight test...");
        runTest();
    }
}
