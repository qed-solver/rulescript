package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.UnionToDistinct;
import org.qed.RuleBuilder;

import java.util.List;

public class UnionToDistinctTest {

    public static void runTest() {
        var rule = new UnionToDistinct();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(UnionToDistinct rule) {
        var base = RuleBuilder.create();
        var ta = RuleChecks.table(base, 1, false);
        var tb = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(ta).scan(tb).union(false, 2).build();
        var after = b.scan(ta).scan(tb).union(true, 2).aggregate(b.groupKey(0)).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(ta).scan(tb).union(true, 2).build());
        RuleChecks.doesNotApply(rule, b.scan(ta).scan(tb).scan(ta).union(false, 3).build());
    }

    public static void main(String[] args) {
        System.out.println("Running UnionToDistinct test...");
        runTest();
    }
}
