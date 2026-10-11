package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushLeakproofFiltersIntoPermeableBarrier;
import org.qed.RuleBuilder;

import java.util.List;

public class PushLeakproofFiltersIntoPermeableBarrierTest {

    public static void runTest() {
        var rule = new PushLeakproofFiltersIntoPermeableBarrier();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushLeakproofFiltersIntoPermeableBarrier rule) {
        var base = RuleBuilder.create();
        var one = RuleChecks.table(base, 1, false);
        var two = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(one)
                .project(List.of(b.field(0)), List.of(), true)
                .filter(b.and(RuleChecks.gt(b, b.field(0), 5),
                        b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(10))))
                .build();
        var after = b.scan(one)
                .filter(RuleChecks.gt(b, b.field(0), 5))
                .project(b.field(0))
                .filter(b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(10)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var skewed = b.scan(two)
                .project(List.of(b.field(1)), List.of(), true)
                .filter(b.and(RuleChecks.gt(b, b.field(0), 5),
                        b.call(SqlStdOperatorTable.LESS_THAN, b.field(0), b.literal(10))))
                .build();
        RuleChecks.doesNotApply(rule, skewed);
    }

    public static void main(String[] args) {
        System.out.println("Running PushLeakproofFiltersIntoPermeableBarrier test...");
        runTest();
    }
}
