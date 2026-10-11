package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateUnionTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateUnionTransposeTest {

    public static void runTest() {
        var rule = new AggregateUnionTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateUnionTranspose rule) {
        var base = RuleBuilder.create();
        var a = RuleChecks.table(base, 2, false);
        var c = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(a).scan(c).union(true, 2).aggregate(b.groupKey(0, 1)).build();
        var after = b.scan(a).aggregate(b.groupKey(0, 1)).scan(c).aggregate(b.groupKey(0, 1)).union(true, 2)
                .aggregate(b.groupKey(0, 1)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule,
                b.scan(a).scan(c).union(false, 2).aggregate(b.groupKey(0, 1)).build());
        RuleChecks.doesNotApply(rule, b.scan(a).scan(c).union(true, 2)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateUnionTranspose test...");
        runTest();
    }
}
