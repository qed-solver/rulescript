package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.UnionEliminator;
import org.qed.RuleBuilder;

import java.util.List;

public class UnionEliminatorTest {

    public static void runTest() {
        var rule = new UnionEliminator();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(UnionEliminator rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = org.apache.calcite.rel.logical.LogicalUnion.create(List.of(b.scan(ta).build()), true);
        var after = b.scan(ta).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(ta).scan(tb).union(true, 2).build());
    }

    public static void main(String[] args) {
        System.out.println("Running UnionEliminator test...");
        runTest();
    }
}
