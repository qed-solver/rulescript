package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ExpandDisjunctionForTable;
import org.qed.RuleBuilder;

import java.util.List;

public class ExpandDisjunctionForTableTest {

    public static void runTest() {
        var rule = new ExpandDisjunctionForTable();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ExpandDisjunctionForTable rule) {
        var base = RuleBuilder.create();
        var t1 = RuleChecks.table(base, 1, false);
        var t2 = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var cond = (java.util.function.Function<RelBuilder, RexNode>) x -> x.and(
                RuleChecks.eq(x, x.field(2, 0, 0), x.field(2, 1, 0)),
                x.or(x.and(RuleChecks.gt(x, x.field(2, 0, 0), 1), RuleChecks.gt(x, x.field(2, 1, 0), 3)),
                        x.and(RuleChecks.gt(x, x.field(2, 0, 0), 2), RuleChecks.gt(x, x.field(2, 1, 0), 4))));
        var before = b.scan(t1).scan(t2).join(JoinRelType.INNER, cond.apply(b)).build();

        b.scan(t1).scan(t2);
        var after = b.join(JoinRelType.INNER, b.and(cond.apply(b),
                b.or(RuleChecks.gt(b, b.field(2, 0, 0), 1), RuleChecks.gt(b, b.field(2, 0, 0), 2)),
                b.or(RuleChecks.gt(b, b.field(2, 1, 0), 3), RuleChecks.gt(b, b.field(2, 1, 0), 4)))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t1).scan(t2).join(JoinRelType.LEFT, cond.apply(b)).build());
        RuleChecks.doesNotApply(rule, b.scan(t1).scan(t2).join(JoinRelType.INNER,
                b.or(b.and(RuleChecks.gt(b, b.field(2, 0, 0), 1), RuleChecks.gt(b, b.field(2, 1, 0), 3)),
                        b.and(RuleChecks.gt(b, b.field(2, 0, 0), 2), RuleChecks.gt(b, b.field(2, 1, 0), 4))))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running ExpandDisjunctionForTable test...");
        runTest();
    }
}
