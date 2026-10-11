package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.FilterSetOpTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterSetOpTransposeTest {

    public static void runTest() {
        var rule = new FilterSetOpTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FilterSetOpTranspose rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 1, false);
        var r = RuleChecks.table(base, 1, false);
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(l).scan(r).union(true, 2).filter(RuleChecks.gt(b, b.field(0), 5)).build();
        var after = b.scan(l).filter(RuleChecks.gt(b, b.field(0), 5)).scan(r)
                .filter(RuleChecks.gt(b, b.field(0), 5)).union(true, 2).build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).union(false, 2).filter(RuleChecks.gt(b, b.field(0), 5)).build());
        RuleChecks.doesNotApply(rule,
                b.scan(l).scan(r).intersect(true, 2).filter(RuleChecks.gt(b, b.field(0), 5)).build());
        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).scan(l).union(true, 3)
                .filter(RuleChecks.gt(b, b.field(0), 5)).build());
    }

    public static void main(String[] args) {
        System.out.println("Running FilterSetOpTranspose test...");
        runTest();
    }
}
