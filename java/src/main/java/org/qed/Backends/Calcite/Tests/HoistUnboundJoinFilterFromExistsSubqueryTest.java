package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.HoistUnboundJoinFilterFromExistsSubquery;
import org.qed.RuleBuilder;

public class HoistUnboundJoinFilterFromExistsSubqueryTest {

    public static void runTest() {
        var rule = new HoistUnboundJoinFilterFromExistsSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(HoistUnboundJoinFilterFromExistsSubquery rule) {
        var builder = RuleBuilder.create();
        var outer = RuleChecks.table(builder, 1, false);
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        var unbound = builder.genericPredicateOp("unbound_cond", false);
        var joined = builder.scan(l).scan(r)
                .join(JoinRelType.INNER, builder.and(
                        builder.call(unbound),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))))
                .build();
        var before = builder.scan(outer).filter(RexSubQuery.exists(joined)).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var innerAfter = plain.scan(l).scan(r)
                .join(JoinRelType.INNER, plain.call(SqlStdOperatorTable.EQUALS, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .build();
        var after = plain.scan(outer)
                .filter(plain.and(RexSubQuery.exists(innerAfter), plain.call(unbound)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var leftJoined = builder.scan(l).scan(r)
                .join(JoinRelType.LEFT, builder.and(
                        builder.call(unbound),
                        builder.call(SqlStdOperatorTable.EQUALS, builder.field(2, 0, 0), builder.field(2, 1, 0))))
                .build();
        RuleChecks.doesNotApply(rule, builder.scan(outer).filter(RexSubQuery.exists(leftJoined)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running HoistUnboundJoinFilterFromExistsSubquery test...");
        runTest();
    }
}
