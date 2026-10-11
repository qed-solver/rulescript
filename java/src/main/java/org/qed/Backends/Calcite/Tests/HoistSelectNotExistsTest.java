package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.HoistSelectNotExists;
import org.qed.RuleBuilder;

public class HoistSelectNotExistsTest {

    public static void runTest() {
        var rule = new HoistSelectNotExists();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(HoistSelectNotExists rule) {
        var builder = RuleBuilder.create();
        var outer = RuleChecks.table(builder, 1, false);
        var inner = RuleChecks.table(builder, 1, false);
        var five = builder.literal(5);
        var ten = builder.literal(10);
        var sub = builder.scan(inner).filter(RuleChecks.gt(builder, builder.field(0), 5)).build();
        var before = builder.scan(outer).filter(builder.and(
                        builder.call(SqlStdOperatorTable.NOT, RexSubQuery.exists(sub)),
                        RuleChecks.gt(builder, builder.field(0), 10)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(outer)
                .scan(inner).filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five))
                .join(JoinRelType.ANTI, plain.literal(true))
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), ten))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var bareNegation = builder.scan(outer)
                .filter(builder.call(SqlStdOperatorTable.NOT, RexSubQuery.exists(sub)))
                .build();
        RuleChecks.doesNotApply(rule, bareNegation);
    }

    public static void main(String[] args) {
        System.out.println("Running HoistSelectNotExists test...");
        runTest();
    }
}
