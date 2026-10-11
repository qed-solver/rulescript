package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.HoistJoinSubquery;
import org.qed.RuleBuilder;

public class HoistJoinSubqueryTest {

    public static void runTest() {
        var rule = new HoistJoinSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(HoistJoinSubquery rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var sub = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var subPlan = builder.scan(sub)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five))
                .build();
        var joined = builder.scan(left).scan(right);
        var before = joined.join(JoinRelType.INNER,
                builder.call(SqlStdOperatorTable.AND,
                        RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                        RexSubQuery.exists(subPlan)))
                .build();

        var plain = RuleChecks.ruleBuilder(builder);
        var plainSub = plain.scan(sub)
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five))
                .build();
        var rightFiltered = plain.scan(right)
                .filter(RexSubQuery.exists(plainSub))
                .build();
        var after = plain.scan(left)
                .push(rightFiltered)
                .join(JoinRelType.INNER, RuleChecks.eq(plain, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var leftJoin = builder.scan(left).scan(right)
                .join(JoinRelType.LEFT,
                        builder.call(SqlStdOperatorTable.AND,
                                RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)),
                                RexSubQuery.exists(subPlan)))
                .build();
        RuleChecks.doesNotApply(rule, leftJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running HoistJoinSubquery test...");
        runTest();
    }
}
