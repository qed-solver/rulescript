package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.ProjectAggregateMerge;
import org.qed.RuleBuilder;

import java.util.List;

public class ProjectAggregateMergeTest {

    public static void runTest() {
        var rule = new ProjectAggregateMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectAggregateMerge rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var aggregate = (java.util.function.Supplier<RelBuilder>) () -> {
            b.scan(t);
            var c = b.field(0);
            return b.aggregate(b.groupKey(b.call(SqlStdOperatorTable.PLUS, c, b.literal(1))),
                    b.aggregateCall(SqlStdOperatorTable.SUM, c), b.aggregateCall(SqlStdOperatorTable.MIN, c),
                    b.aggregateCall(SqlStdOperatorTable.MAX, c));
        };
        var before = aggregate.get().project(b.field(0), b.field(1), b.field(3)).build();
        b.scan(t);
        var c = b.field(0);
        var after = b.aggregate(b.groupKey(b.call(SqlStdOperatorTable.PLUS, c, b.literal(1))),
                b.aggregateCall(SqlStdOperatorTable.SUM, c), b.aggregateCall(SqlStdOperatorTable.MAX, c))
                .project(b.field(0), b.field(1), b.field(2)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule,
                aggregate.get().project(b.field(0), b.field(1), b.field(2), b.field(3)).build());
        RuleChecks.doesNotApply(rule, aggregate.get().project(b.field(0), b.field(2), b.field(3)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectAggregateMerge test...");
        runTest();
    }
}
