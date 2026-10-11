package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateValues;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateValuesTest {

    public static void runTest() {
        var rule = new AggregateValues();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateValues rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(t).aggregate(b.groupKey(0)).build();
        var after = b.scan(t).scan(t).union(false, 2).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(t)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.COUNT, b.field(0))).build());
        var wide = RuleChecks.table(base, 2, false);
        RuleChecks.doesNotApply(rule, b.scan(wide).aggregate(b.groupKey(0)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateValues test...");
        runTest();
    }
}
