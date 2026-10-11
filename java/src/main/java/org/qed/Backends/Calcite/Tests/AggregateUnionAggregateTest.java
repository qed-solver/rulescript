package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateUnionAggregate;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateUnionAggregateTest {

    public static void runTest() {
        var rule = new AggregateUnionAggregate();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateUnionAggregate rule) {
        var base = RuleBuilder.create();
        var a = RuleChecks.table(base, 1, false);
        var l = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(a).scan(l).aggregate(b.groupKey(0)).union(true, 2).aggregate(b.groupKey(0)).build();
        var after = b.scan(a).scan(l).union(true, 2).aggregate(b.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule,
                b.scan(a).aggregate(b.groupKey(0)).scan(l).union(true, 2).aggregate(b.groupKey(0)).build());
        RuleChecks.doesNotApply(rule,
                b.scan(a).scan(l).aggregate(b.groupKey(0)).union(false, 2).aggregate(b.groupKey(0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateUnionAggregate test...");
        runTest();
    }
}
