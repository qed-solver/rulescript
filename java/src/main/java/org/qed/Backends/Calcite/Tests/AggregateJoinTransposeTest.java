package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateJoinTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateJoinTransposeTest {

    public static void runTest() {
        var rule = new AggregateJoinTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateJoinTranspose rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 2, false);
        var r = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var join = (java.util.function.Function<JoinRelType, RelBuilder>) type -> b.scan(l).scan(r)
                .join(type, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)));
        var before = join.apply(JoinRelType.INNER).aggregate(b.groupKey(1, 3)).build();
        var after = b.scan(l).aggregate(b.groupKey(0, 1)).scan(r).aggregate(b.groupKey(0, 1))
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .aggregate(b.groupKey(1, 3)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, join.apply(JoinRelType.LEFT).aggregate(b.groupKey(1, 3)).build());
        RuleChecks.doesNotApply(rule, join.apply(JoinRelType.INNER).aggregate(b.groupKey(0, 3)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateJoinTranspose test...");
        runTest();
    }
}
