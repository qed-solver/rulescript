package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.EliminateAggDistinct;
import org.qed.RuleBuilder;

public class EliminateAggDistinctTest {

    public static void runTest() {
        var rule = new EliminateAggDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        keyedTable(rule);
    }

    private static void keyedTable(EliminateAggDistinct rule) {
        var base = RuleBuilder.create();
        var keyed = RuleChecks.table(base, 1, true, false);
        var plain = RuleChecks.table(base, 1, false, false);
        var b = RuleChecks.ruleBuilder(base);

        var before = b.scan(keyed)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MIN, true, (RexNode) null, (String) null, b.field(0)))
                .build();
        var after = b.scan(keyed)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MIN, b.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(keyed)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MIN, b.field(0)))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(plain)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.MIN, true, (RexNode) null, (String) null, b.field(0)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAggDistinct test...");
        runTest();
    }
}
