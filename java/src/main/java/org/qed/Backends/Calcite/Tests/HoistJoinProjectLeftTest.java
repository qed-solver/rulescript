package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.HoistJoinProjectLeft;
import org.qed.RuleBuilder;

public class HoistJoinProjectLeftTest {

    public static void runTest() {
        var rule = new HoistJoinProjectLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(HoistJoinProjectLeft rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(l).project(b.field(1), b.field(0))
                .scan(r)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build();
        var after = b.scan(l).scan(r)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 1), b.field(2, 1, 0)))
                .project(b.field(1), b.field(0), b.field(2))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).project(b.field(1), b.field(0))
                .scan(r)
                .join(JoinRelType.LEFT, b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(l).project(b.field(0), b.field(0))
                .scan(r)
                .join(JoinRelType.INNER, b.call(SqlStdOperatorTable.GREATER_THAN, b.field(2, 0, 0), b.field(2, 1, 0)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running HoistJoinProjectLeft test...");
        runTest();
    }
}
