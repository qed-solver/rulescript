package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PruneWithScanCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

import java.util.List;

public class PruneWithScanColsTest {

    public static void runTest() {
        var rule = new PruneWithScanCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneWithScanCols rule) {
        var base = RuleBuilder.create();
        var t = RuleChecks.table(base, 3, false);
        var two = RuleChecks.table(base, 2, false);
        var top = base.genericProjectionOp("Top", new RelType.VarType("Top_Type", true));
        var b = RuleChecks.ruleBuilder(base);

        var before = base.scan(t)
                .project(base.call(top, base.field(0), base.field(1)))
                .build();
        var after = b.scan(t)
                .project(List.of(b.field(0), b.field(1)))
                .project(b.call(top, b.field(0), b.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        RuleChecks.doesNotApply(rule, base.scan(t)
                .project(base.call(top, base.field(0), base.field(2)))
                .build());
        RuleChecks.doesNotApply(rule, base.scan(two)
                .project(base.call(top, base.field(0), base.field(1)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running PruneWithScanCols test...");
        runTest();
    }
}
