package org.qed.Backends.Calcite.Tests;

import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateAggDistinctForKeys;
import org.qed.RelType;

import static org.qed.Backends.Calcite.Tests.RuleChecks.doesNotApply;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rejectsNearMisses;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewrites;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesConcretePredicates;
import static org.qed.Backends.Calcite.Tests.RuleChecks.rewritesPattern;
import static org.qed.Backends.Calcite.Tests.RuleChecks.table;

public class EliminateAggDistinctForKeysTest {
    public static void runTest() {
        var rule = new EliminateAggDistinctForKeys();
        rewritesPattern(rule);
        rewritesConcretePredicates(rule);
        rejectsNearMisses(rule);

        var before = aggregate(true, true);
        var after = aggregate(true, false);
        rewrites(rule, before, after);
        doesNotApply(rule, aggregate(false, true));
    }

    private static org.apache.calcite.rel.RelNode aggregate(boolean unique, boolean distinct) {
        var builder = org.qed.RuleBuilder.create();
        var source = table(builder, 1, unique, false);
        var type = RelType.fromString("INTEGER", true);
        builder.scan(source).aggregate(builder.groupKey(builder.field(0)), builder.aggregateCall(
                builder.genericAggregateOp("f", type), distinct, null, "f", java.util.List.of(builder.field(0))));
        return builder.build();
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateAggDistinctForKeys test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
