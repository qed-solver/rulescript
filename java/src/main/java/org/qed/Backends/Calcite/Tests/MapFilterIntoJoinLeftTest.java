package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRule;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.MapFilterIntoJoinLeft;

public final class MapFilterIntoJoinLeftTest {
    public static void runTest() {
        var rule = new MapFilterIntoJoinLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RRule rule) {
        var beforeSchema = RuleBuilder.create();
        var leftName = RuleChecks.table(beforeSchema, 2, false, false);
        var rightName = RuleChecks.table(beforeSchema, 1, false, false);
        var beforeBuilder = RuleChecks.ruleBuilder(beforeSchema);
        var predicate = RuleBuilder.create().genericPredicateOp("filter_item", true);
        var before = join(beforeBuilder, leftName, rightName, JoinRelType.INNER, predicate, false);

        var expectedSchema = RuleBuilder.create();
        var expectedLeftName = RuleChecks.table(expectedSchema, 2, false, false);
        var expectedRightName = RuleChecks.table(expectedSchema, 1, false, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedSchema);
        var expected = join(expectedBuilder, expectedLeftName, expectedRightName, JoinRelType.INNER, predicate, true);
        RuleChecks.rewrites(rule, before, expected);

        var nearMissSchema = RuleBuilder.create();
        var nearMissLeftName = RuleChecks.table(nearMissSchema, 2, false, false);
        var nearMissRightName = RuleChecks.table(nearMissSchema, 1, false, false);
        var nearMissBuilder = RuleChecks.ruleBuilder(nearMissSchema);
        var nearMiss = join(nearMissBuilder, nearMissLeftName, nearMissRightName, JoinRelType.LEFT, predicate, false);
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static org.apache.calcite.rel.RelNode join(RelBuilder builder, String leftName, String rightName,
                                                       JoinRelType type, org.apache.calcite.sql.SqlOperator predicate,
                                                       boolean rebound) {
        builder.scan(leftName);
        builder.scan(rightName);
        var leftKey = builder.field(2, 0, 0);
        var leftValue = builder.field(2, 0, 1);
        var rightKey = builder.field(2, 1, 0);
        var mappedKey = rebound ? leftKey : rightKey;
        var condition = builder.and(
                builder.call(SqlStdOperatorTable.EQUALS, leftKey, rightKey),
                builder.call(predicate, mappedKey, leftValue));
        builder.join(type, condition);
        return builder.build();
    }

    public static void main(String[] args) {
        System.out.println("Running RULE test...");
        runTest();
    }
}
