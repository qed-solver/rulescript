package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.InlineJoinConstantsLeft;
import org.qed.RuleBuilder;

public class InlineJoinConstantsLeftTest {

    public static void runTest() {
        var rule = new InlineJoinConstantsLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(InlineJoinConstantsLeft rule) {
        var builder = RuleBuilder.create();
        var source = RuleChecks.table(builder, 1, false);
        var constant = RuleChecks.table(builder, 1, true);
        var right = RuleChecks.table(builder, 1, false);
        var before = builder.scan(source).scan(constant)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .scan(right)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(source).scan(constant)
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .scan(right)
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(2, 0, 1), plain.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nonUniqueConstant = RuleChecks.table(builder, 1, false);
        var nonKey = builder.scan(source).scan(nonUniqueConstant)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .scan(right)
                .join(JoinRelType.INNER, builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, nonKey);
    }

    public static void main(String[] args) {
        System.out.println("Running InlineJoinConstantsLeft test...");
        runTest();
    }
}
