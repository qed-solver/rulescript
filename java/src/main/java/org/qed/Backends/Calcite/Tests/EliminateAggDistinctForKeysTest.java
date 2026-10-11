package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.EliminateAggDistinctForKeys;
import org.qed.RuleBuilder;

public class EliminateAggDistinctForKeysTest {

    public static void runTest() {
        var rule = new EliminateAggDistinctForKeys();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateAggDistinctForKeys rule) {
        var builder = RuleBuilder.create();
        var keyed = RuleChecks.table(builder, 1, true, false);
        var plain = RuleChecks.table(builder, 1, false, false);
        var before = builder.scan(keyed)
                .aggregate(builder.groupKey(0),
                        builder.aggregateCall(SqlStdOperatorTable.COUNT, true, null, null, builder.field(0)))
                .build();
        var rewritten = RuleChecks.ruleBuilder(builder);
        var after = rewritten.scan(keyed)
                .aggregate(rewritten.groupKey(0),
                        rewritten.aggregateCall(SqlStdOperatorTable.COUNT, false, null, null, rewritten.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nonKey = builder.scan(plain)
                .aggregate(builder.groupKey(0),
                        builder.aggregateCall(SqlStdOperatorTable.COUNT, true, null, null, builder.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, nonKey);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAggDistinctForKeys test...");
        runTest();
    }
}
