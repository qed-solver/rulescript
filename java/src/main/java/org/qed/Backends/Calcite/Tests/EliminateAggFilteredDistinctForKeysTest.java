package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.EliminateAggFilteredDistinctForKeys;
import org.qed.RuleBuilder;

public class EliminateAggFilteredDistinctForKeysTest {

    public static void runTest() {
        var rule = new EliminateAggFilteredDistinctForKeys();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateAggFilteredDistinctForKeys rule) {
        var base = RuleBuilder.create();
        var keyed = RuleChecks.table(base, 1, true);
        var plain = RuleChecks.table(base, 1, false);
        var wide = RuleChecks.table(base, 2, true);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(keyed)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, true, (RexNode) null, (String) null, b.field(0)))
                .build();
        var after = b.scan(keyed)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(plain)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, true, (RexNode) null, (String) null, b.field(0)))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(keyed)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, b.field(0)))
                .build());
        RuleChecks.doesNotApply(rule, b.scan(wide)
                .aggregate(b.groupKey(0), b.aggregateCall(SqlStdOperatorTable.SUM, true, (RexNode) null, (String) null, b.field(1)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAggFilteredDistinctForKeys test...");
        runTest();
    }
}
