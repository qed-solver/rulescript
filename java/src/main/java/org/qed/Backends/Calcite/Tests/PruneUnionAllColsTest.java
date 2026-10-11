package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PruneUnionAllCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PruneUnionAllColsTest {

    public static void runTest() {
        var rule = new PruneUnionAllCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneUnionAllCols rule) {
        var base = RuleBuilder.create();
        var l = RuleChecks.table(base, 3, false);
        var r = RuleChecks.table(base, 3, false);
        var g = base.genericProjectionOp("G", new RelType.VarType("G_Type", true));
        var b = RuleChecks.ruleBuilder(base);
        var before = b.scan(l).scan(r).union(true, 2)
                .project(b.call(g, b.field(0), b.field(2)))
                .build();
        var after = b.scan(l).project(b.field(0), b.field(2))
                .scan(r).project(b.field(0), b.field(2))
                .union(true, 2)
                .project(b.call(g, b.field(0), b.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, b.scan(l).scan(r).union(false, 2)
                .project(b.call(g, b.field(0), b.field(2))).build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneUnionAllCols test...");
        runTest();
    }
}
