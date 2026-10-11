package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.AggregateMerge;
import org.qed.RuleBuilder;

import java.util.List;

public class AggregateMergeTest {

    public static void runTest() {
        var rule = new AggregateMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(AggregateMerge rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var inner = (java.util.function.Supplier<RelBuilder>) () -> {
            b.scan(t);
            var c = b.field(0);
            return b.aggregate(b.groupKey(b.call(SqlStdOperatorTable.PLUS, c, b.literal(1)),
                            b.call(SqlStdOperatorTable.MULTIPLY, c, b.literal(2))),
                    b.aggregateCall(SqlStdOperatorTable.SUM, c));
        };
        var before = inner.get().aggregate(b.groupKey(0)).build();
        b.scan(t);
        var after = b.aggregate(b.groupKey(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)))).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, inner.get().aggregate(b.groupKey(1)).build());
        RuleChecks.doesNotApply(rule, inner.get()
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MAX, b.field(2))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running AggregateMerge test...");
        runTest();
    }
}
