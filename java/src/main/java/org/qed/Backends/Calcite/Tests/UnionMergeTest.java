package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.UnionMerge;
import org.qed.RuleBuilder;

public class UnionMergeTest {

    public static void runTest() {
        var rule = new UnionMerge();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(UnionMerge rule) {
        var builder = RuleBuilder.create();
        var t = new String[4];
        for (var i = 0; i < t.length; i++) {
            t[i] = RuleChecks.table(builder, 1, false);
        }
        var nestedSecond = builder.scan(t[0]).scan(t[1]).scan(t[2]).union(true, 2).scan(t[3]).union(true, 3).build();
        var flat = builder.scan(t[0]).scan(t[1]).scan(t[2]).scan(t[3]).union(true, 4).build();
        RuleChecks.rewrites(rule, nestedSecond, flat);
        var nestedFirst = builder.scan(t[0]).scan(t[1]).union(true, 2).scan(t[2]).scan(t[3]).union(true, 3).build();
        RuleChecks.doesNotApply(rule, nestedFirst);
    }

    public static void main(String[] args) {
        System.out.println("Running UnionMerge test...");
        runTest();
    }
}
