package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterCorrelate;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterCorrelateTest {

    public static void runTest() {
        var rule = new FilterCorrelate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FilterCorrelate rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var product = (java.util.function.Function<RexNode, RelBuilder>) cond ->
                b.scan(l).scan(r).join(JoinRelType.INNER, cond);
        var conjuncts = (java.util.function.Supplier<RexNode>) () -> b.and(RuleChecks.gt(b, b.field(0), 5),
                RuleChecks.gt(b, b.field(1), 6), RuleChecks.eq(b, b.field(0), b.field(1)));
        var before = product.apply(b.literal(true)).filter(conjuncts.get()).build();
        var after = b.scan(l).filter(RuleChecks.gt(b, b.field(0), 5)).scan(r).filter(RuleChecks.gt(b, b.field(0), 6))
                .join(JoinRelType.INNER, b.literal(true)).filter(RuleChecks.eq(b, b.field(0), b.field(1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).join(JoinRelType.LEFT, b.literal(true))
                .filter(conjuncts.get()).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .filter(conjuncts.get()).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FilterCorrelate test...");
        runTest();
    }
}
