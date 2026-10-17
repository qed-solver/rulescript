package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.RRuleInstances.ProjectJoinRemove;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

public class ProjectJoinRemoveTest {
    public static void runTest() {
        var rule = new ProjectJoinRemove();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(ProjectJoinRemove rule) {
        var qed = RuleBuilder.create();
        var unique = RuleChecks.table(qed, 1, true, false);
        var other = RuleChecks.table(qed, 1, true, false);
        var b = RuleChecks.ruleBuilder(qed);
        var rex = b.getRexBuilder();
        var scan = b.scan(unique).build();
        var type = scan.getRowType().getFieldList().get(0).getType();
        var onKey = RuleChecks.eq(b, rex.makeInputRef(type, 0), rex.makeInputRef(type, 1));

        var join = LogicalJoin.create(scan, scan, List.of(), onKey, Set.of(), JoinRelType.LEFT);
        var before = LogicalProject.create(join, List.of(), List.of(rex.makeInputRef(join, 0)), List.of("c"));
        RuleChecks.rewrites(rule, before, scan);

        var inner = LogicalJoin.create(scan, scan, List.of(), onKey, Set.of(), JoinRelType.INNER);
        RuleChecks.doesNotApply(rule,
                LogicalProject.create(inner, List.of(), List.of(rex.makeInputRef(inner, 0)), List.of("c")));

        var otherScan = b.scan(other).build();
        var differentTables = LogicalJoin.create(scan, otherScan, List.of(), onKey, Set.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, LogicalProject.create(differentTables, List.of(),
                List.of(rex.makeInputRef(differentTables, 0)), List.of("c")));
    }

    public static void main(String[] args) {
        System.out.println("Running ProjectJoinRemove test...");
        runTest();
    }
}
