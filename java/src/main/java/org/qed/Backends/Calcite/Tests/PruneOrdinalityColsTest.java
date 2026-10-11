package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PruneOrdinalityCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PruneOrdinalityColsTest {

    public static void runTest() {
        var rule = new PruneOrdinalityCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PruneOrdinalityCols rule) {
        var builder = RuleBuilder.create();
        var three = RuleChecks.table(builder, 3, false);
        var oOp = builder.genericProjectionOp("O", RelType.fromString("INTEGER", true));
        var gOp = builder.genericProjectionOp("G", RelType.fromString("INTEGER", true));
        var before = builder.scan(three)
                .project(builder.field(0), builder.field(1), builder.field(2),
                        builder.call(oOp, builder.field(0), builder.field(1)))
                .project(builder.call(gOp, builder.field(0), builder.field(1), builder.field(3)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(three)
                .project(plain.field(0), plain.field(1))
                .project(plain.field(0), plain.field(1), plain.call(oOp, plain.field(0), plain.field(1)))
                .project(plain.call(gOp, plain.field(0), plain.field(1), plain.field(2)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nearMiss = builder.scan(three)
                .project(builder.field(0), builder.field(1), builder.field(2),
                        builder.call(oOp, builder.field(0), builder.field(2)))
                .project(builder.call(gOp, builder.field(0), builder.field(1), builder.field(3)))
                .build();
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneOrdinalityCols test...");
        runTest();
    }
}
