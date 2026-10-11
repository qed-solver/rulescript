package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FilterJoin;
import org.qed.RuleBuilder;

public class FilterJoinTest {

    public static void runTest() {
        var rule = new FilterJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FilterJoin rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        for (var type : JoinRelType.values()) {
            if (type != JoinRelType.INNER && type != JoinRelType.LEFT) {
                continue;
            }
            var on = builder.call(SqlStdOperatorTable.EQUALS, builder.scan(l).scan(r).field(2, 0, 0),
                    builder.field(2, 1, 0));
            var above = builder.call(SqlStdOperatorTable.GREATER_THAN, builder.field(0), builder.literal(1));
            var plan = builder.join(type, on).filter(above).build();
            if (type == JoinRelType.LEFT) {
                RuleChecks.doesNotApply(rule, plan);
                continue;
            }
            var plain = RuleChecks.ruleBuilder(builder);
            var merged = plain.scan(l).scan(r).join(JoinRelType.INNER,
                    plain.and(plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)),
                            plain.call(SqlStdOperatorTable.GREATER_THAN, plain.field(2, 0, 0), plain.literal(1)))).build();
            RuleChecks.rewrites(rule, plan, merged);
        }
    }

    public static void main(String[] args) {
        System.out.println("Running FilterJoin test...");
        runTest();
    }
}
