package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.HoistSelectExists;
import org.qed.RuleBuilder;

public class HoistSelectExistsTest {

    public static void runTest() {
        var rule = new HoistSelectExists();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(HoistSelectExists rule) {
        var builder = RuleBuilder.create();
        var outer = RuleChecks.table(builder, 1, false);
        var inner = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var ten = builder.literal(10);
        var subquery = builder.scan(inner)
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five))
                .build();
        var before = builder.scan(outer)
                .filter(builder.call(SqlStdOperatorTable.AND,
                        RexSubQuery.exists(subquery),
                        builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), ten)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(outer)
                .scan(inner)
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five))
                .join(JoinRelType.SEMI, plain.literal(true))
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), ten))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var single = builder.scan(outer)
                .filter(RexSubQuery.exists(subquery))
                .build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running HoistSelectExists test...");
        runTest();
    }
}
