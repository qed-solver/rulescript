package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushSelectIntoOrdinality;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PushSelectIntoOrdinalityTest {

    public static void runTest() {
        var rule = new PushSelectIntoOrdinality();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushSelectIntoOrdinality rule) {
        var builder = RuleBuilder.create();
        var two = RuleChecks.table(builder, 2, false);
        var oOp = builder.genericProjectionOp("O", RelType.fromString("INTEGER", true));
        var five = builder.literal(5);
        var before = builder.scan(two)
                .project(builder.field(0), builder.field(1),
                        builder.call(oOp, builder.field(0), builder.field(1)))
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), five))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(two)
                .filter(plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(0), five))
                .project(plain.field(0), plain.field(1),
                        plain.call(oOp, plain.field(0), plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(two)
                .project(builder.field(0), builder.field(1),
                        builder.call(oOp, builder.field(0), builder.field(1)))
                .filter(builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(1), five))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushSelectIntoOrdinality test...");
        runTest();
    }
}
