package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.IntersectReorder;
import org.qed.RuleBuilder;

import java.util.List;

public class IntersectReorderTest {

    public static void runTest() {
        var rule = new IntersectReorder();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(IntersectReorder rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var tc = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(ta).scan(tb).scan(tc).intersect(false, 3).build();
        var after = b.scan(tc).scan(ta).scan(tb).intersect(false, 3).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(ta).scan(tb).scan(tc).intersect(true, 3).build());
        RuleChecks.doesNotApply(rule, b.scan(ta).scan(tb).intersect(false, 2).build());
        RuleChecks.doesNotApply(rule, b.scan(ta).scan(tb).scan(tc).union(false, 3).build());
    }

    public static void main(String[] args) {
        System.out.println("Running IntersectReorder test...");
        runTest();
    }
}
