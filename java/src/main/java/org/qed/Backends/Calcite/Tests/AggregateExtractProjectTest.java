package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateExtractProject;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateExtractProjectTest {

    public static void runTest() {
        var rule = new AggregateExtractProject();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateExtractProject rule) {
        var base = RuleBuilder.create();
        var a = RuleChecks.table(base, 1, false);
        var bb = RuleChecks.table(base, 1, false);
        var c = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var threeColumns = (java.util.function.Supplier<RelBuilder>) () ->
                b.scan(a).scan(bb).join(JoinRelType.INNER, b.literal(true)).scan(c)
                        .join(JoinRelType.INNER, b.literal(true));
        var before = threeColumns.get().aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1)))
                .build();
        var after = threeColumns.get().project(b.field(0), b.field(1))
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(1))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, threeColumns.get()
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(2))).build());
        RuleChecks.doesNotApply(rule, threeColumns.get()
                .aggregate(b.groupKey(1), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(0))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateExtractProject test...");
        runTest();
    }
}
