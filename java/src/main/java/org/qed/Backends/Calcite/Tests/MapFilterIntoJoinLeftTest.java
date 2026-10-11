package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.MapFilterIntoJoinLeft;
import org.qed.RuleBuilder;

public class MapFilterIntoJoinLeftTest {

    public static void runTest() {
        var rule = new MapFilterIntoJoinLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(MapFilterIntoJoinLeft rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 2, false, false);
        var right = RuleChecks.table(builder, 1, false, false);
        var before = builder.scan(left).scan(right)
                .join(JoinRelType.INNER, builder.and(
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 1, 0), builder.field(2, 0, 1))))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(left).scan(right)
                .join(JoinRelType.INNER, plain.and(
                        plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)),
                        plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(2, 0, 0), plain.field(2, 0, 1))))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var outer = builder.scan(left).scan(right)
                .join(JoinRelType.LEFT, builder.and(
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(2, 1, 0), builder.field(2, 0, 1))))
                .build();
        RuleChecks.doesNotApply(rule, outer);
    }

    public static void main(String[] args) {
        System.out.println("Running MapFilterIntoJoinLeft test...");
        runTest();
    }
}
