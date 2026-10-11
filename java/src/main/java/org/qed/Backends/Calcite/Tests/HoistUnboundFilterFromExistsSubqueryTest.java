package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexSubQuery;
import org.qed.RRuleInstances.HoistUnboundFilterFromExistsSubquery;
import org.qed.RuleBuilder;

public class HoistUnboundFilterFromExistsSubqueryTest {

    public static void runTest() {
        var rule = new HoistUnboundFilterFromExistsSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(HoistUnboundFilterFromExistsSubquery rule) {
        var builder = RuleBuilder.create();
        var outer = RuleChecks.table(builder, 1, false);
        var inner = RuleChecks.table(builder, 1, false);
        var unboundOp = builder.genericPredicateOp("unbound_cond", false);
        var before = builder.scan(outer)
                .filter(RexSubQuery.exists(builder.scan(inner)
                        .filter(builder.and(builder.call(unboundOp),
                                RuleChecks.gt(builder, builder.field(0), 5)))
                        .build()))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(outer)
                .filter(plain.and(
                        RexSubQuery.exists(plain.scan(inner)
                                .filter(RuleChecks.gt(plain, plain.field(0), 5))
                                .build()),
                        plain.call(unboundOp)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var single = builder.scan(outer)
                .filter(RexSubQuery.exists(builder.scan(inner)
                        .filter(RuleChecks.gt(builder, builder.field(0), 5))
                        .build()))
                .build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running HoistUnboundFilterFromExistsSubquery test...");
        runTest();
    }
}
