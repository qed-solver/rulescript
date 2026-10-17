package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.RRuleInstances.EliminateJoinNoColsLeft;
import org.qed.RuleBuilder;

import java.util.List;

public class EliminateJoinNoColsLeftTest {
    public static void runTest() {
        var rule = new EliminateJoinNoColsLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(EliminateJoinNoColsLeft rule) {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false);
        var b = RuleChecks.ruleBuilder(qed);
        var rex = b.getRexBuilder();
        var scan = b.scan(table).build();
        var condition = RuleChecks.gt(b, rex.makeInputRef(scan, 0), 0);

        var oneRow = LogicalProject.create(b.values(new String[]{"_r"}, true).build(), List.of(), List.of(), List.of());
        var before = LogicalJoin.create(oneRow, scan, List.of(), condition, java.util.Set.of(), JoinRelType.INNER);
        var after = LogicalFilter.create(scan, condition);
        RuleChecks.rewrites(rule, before, after);

        var twoRows = LogicalProject.create(b.values(new String[]{"_r"}, false).build(), List.of(), List.of(), List.of());
        var other = LogicalJoin.create(twoRows, scan, List.of(), condition, java.util.Set.of(), JoinRelType.INNER);
        RuleChecks.doesNotApply(rule, other);

        var left = LogicalJoin.create(oneRow, scan, List.of(), condition, java.util.Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, left);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinNoColsLeft test...");
        runTest();
    }
}
