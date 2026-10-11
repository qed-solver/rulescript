package org.qed.Backends.Calcite.Tests;

import org.qed.RRuleInstances.PruneMutationInputCols;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class PruneMutationInputColsTest {

    public static void runTest() {
        var rule = new PruneMutationInputCols();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        twoColumnTable(rule);
    }

    private static void twoColumnTable(PruneMutationInputCols rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var mOp = builder.genericProjectionOp("M", RelType.fromString("INTEGER", true));
        var before = builder.scan(table)
                .project(builder.call(mOp, builder.field(0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table)
                .project(plain.field(0))
                .project(plain.call(mOp, plain.field(0)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var twoProjections = builder.scan(table)
                .project(builder.call(mOp, builder.field(0)), builder.field(1))
                .build();
        RuleChecks.doesNotApply(rule, twoProjections);
    }

    public static void main(String[] args) {
        System.out.println("Running PruneMutationInputCols test...");
        runTest();
    }
}
