package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushLeakproofProjectionsIntoPermeableBarrier;
import org.qed.RuleBuilder;

import java.util.List;

public class PushLeakproofProjectionsIntoPermeableBarrierTest {

    public static void runTest() {
        var rule = new PushLeakproofProjectionsIntoPermeableBarrier();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushLeakproofProjectionsIntoPermeableBarrier rule) {
        var base = RuleBuilder.create();
        var one = RuleChecks.table(base, 1, false);
        var two = RuleChecks.table(base, 2, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(one).project(List.of(b.field(0)), List.of(), true)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)), b.field(0)).build();
        var after = b.scan(one)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)), b.field(0)).build();
        RuleChecks.rewrites(rule, before, after);

        var wide = b.scan(two).project(List.of(b.field(0)), List.of(), true)
                .project(b.call(SqlStdOperatorTable.PLUS, b.field(0), b.literal(1)), b.field(0)).build();
        RuleChecks.doesNotApply(rule, wide);
    }

    public static void main(String[] args) {
        System.out.println("Running PushLeakproofProjectionsIntoPermeableBarrier test...");
        runTest();
    }
}
