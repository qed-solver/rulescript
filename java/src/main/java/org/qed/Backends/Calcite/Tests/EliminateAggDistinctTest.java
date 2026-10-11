package org.qed.Backends.Calcite.Tests;

import java.util.List;

import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.logical.LogicalAggregate;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.ImmutableBitSet;
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

    private static RelNode sumOverKey(RelBuilder b, String table, boolean distinct) {
        var input = b.scan(table).build();
        var call = AggregateCall.create(SqlStdOperatorTable.SUM, distinct, false, false, List.of(0), -1, null,
                RelCollations.EMPTY, 1, input, null, null);
        return LogicalAggregate.create(input, List.of(), ImmutableBitSet.of(0), null, List.of(call));
    }

    private static void keyedTable(EliminateAggDistinct rule) {
        var base = RuleBuilder.create();
        var keyed = RuleChecks.table(base, 1, true, false);
        var plain = RuleChecks.table(base, 1, false, false);
        var b = RuleChecks.ruleBuilder(base);

        RuleChecks.rewrites(rule, sumOverKey(b, keyed, true), sumOverKey(b, keyed, false));

        RuleChecks.doesNotApply(rule, sumOverKey(b, keyed, false));
        RuleChecks.doesNotApply(rule, sumOverKey(b, plain, true));
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAggDistinct test...");
        runTest();
    }
}
