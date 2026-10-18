package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalProject;
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
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false);
        var leakproof = qed.genericPredicateOp("Leakproof", true);
        var remaining = qed.genericPredicateOp("Remaining", true);
        var b = RuleChecks.ruleBuilder(qed);
        var rex = b.getRexBuilder();
        var scan = b.scan(table).build();
        var barrier = LogicalProject.create(scan, List.of(), List.of(rex.makeInputRef(scan, 0)), List.of("c"));
        var ref = rex.makeInputRef(barrier, 0);

        var both = rex.makeCall(SqlStdOperatorTable.AND, rex.makeCall(leakproof, ref), rex.makeCall(remaining, ref));
        var before = LogicalFilter.create(barrier, both);

        var pushed = LogicalFilter.create(scan, rex.makeCall(leakproof, rex.makeInputRef(scan, 0)));
        var after = LogicalFilter.create(pushed, rex.makeCall(remaining, rex.makeInputRef(pushed, 0)));
        RuleChecks.rewrites(rule, before, after);

        var single = LogicalFilter.create(barrier, rex.makeCall(leakproof, ref));
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running PushLeakproofFiltersIntoPermeableBarrier test...");
        runTest();
    }
}
