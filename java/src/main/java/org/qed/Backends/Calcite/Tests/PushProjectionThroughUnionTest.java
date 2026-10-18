package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushProjectionThroughUnion;
import org.qed.RuleBuilder;

public class PushProjectionThroughUnionTest {
    public static void runTest() {
        var rule = new PushProjectionThroughUnion();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushProjectionThroughUnion rule) {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 2, false);
        var right = RuleChecks.table(builder, 2, false);
        var plan = builder.scan(left).scan(right).union(true, 2)
                .project(builder.call(SqlStdOperatorTable.PLUS, builder.field(1, 0, 0), builder.field(1, 0, 1)))
                .build();

        var plain = RuleChecks.ruleBuilder(builder);
        var expected = plain.scan(left)
                .project(plain.call(SqlStdOperatorTable.PLUS, plain.field(1, 0, 0), plain.field(1, 0, 1)))
                .scan(right)
                .project(plain.call(SqlStdOperatorTable.PLUS, plain.field(1, 0, 0), plain.field(1, 0, 1)))
                .union(true, 2)
                .build();
        RuleChecks.rewrites(rule, plan, expected);

        var missBuilder = RuleBuilder.create();
        var missLeft = RuleChecks.table(missBuilder, 2, false);
        var missRight = RuleChecks.table(missBuilder, 2, false);
        var miss = missBuilder.scan(missLeft).scan(missRight).union(false, 2)
                .project(missBuilder.call(SqlStdOperatorTable.PLUS, missBuilder.field(1, 0, 0), missBuilder.field(1, 0, 1)))
                .build();
        RuleChecks.doesNotApply(rule, miss);
    }

    public static void main(String[] args) {
        System.out.println("Running PushProjectionThroughUnion test...");
        runTest();
    }
}
