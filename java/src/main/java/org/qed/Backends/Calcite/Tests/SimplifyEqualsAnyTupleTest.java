package org.qed.Backends.Calcite.Tests;

import com.google.common.collect.ImmutableList;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexSubQuery;
import org.qed.RRuleInstances.SimplifyEqualsAnyTuple;
import org.qed.RuleBuilder;

public class SimplifyEqualsAnyTupleTest {

    public static void runTest() {
        var rule = new SimplifyEqualsAnyTuple();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(SimplifyEqualsAnyTuple rule) {
        var base = RuleBuilder.create();
        var s = RuleChecks.table(base, 1, false);
        var v = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var outer = b.scan(s).build();
        var tuple = b.scan(v).build();
        var before = b.push(outer).push(tuple)
                .join(JoinRelType.SEMI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build();
        var membership = RexSubQuery.in(tuple, ImmutableList.of(b.push(outer).field(0)));
        var after = LogicalFilter.create(b.build(), membership);
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.push(outer).push(tuple)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build());
        RuleChecks.doesNotApply(rule, b.push(outer).push(tuple)
                .join(JoinRelType.ANTI, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyEqualsAnyTuple test...");
        runTest();
    }
}
