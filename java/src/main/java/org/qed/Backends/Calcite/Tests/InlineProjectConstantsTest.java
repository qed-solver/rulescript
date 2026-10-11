package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.InlineProjectConstants;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class InlineProjectConstantsTest {

    public static void runTest() {
        var rule = new InlineProjectConstants();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(InlineProjectConstants rule) {
        var builder = RuleBuilder.create();
        var source = RuleChecks.table(builder, 1, false);
        var keyed = RuleChecks.table(builder, 1, true);
        var plain = RuleChecks.table(builder, 1, false);
        var fOp = builder.genericProjectionOp("f", RelType.fromString("INTEGER", true));
        var before = builder.scan(source).scan(keyed)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .project(builder.call(fOp, builder.field(0)))
                .build();
        var b = RuleChecks.ruleBuilder(builder);
        var after = b.scan(source).scan(keyed)
                .join(JoinRelType.INNER, RuleChecks.eq(b, b.field(2, 0, 0), b.field(2, 1, 0)))
                .project(b.call(fOp, b.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nonKeyed = builder.scan(source).scan(plain)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .project(builder.call(fOp, builder.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, nonKeyed);
    }

    public static void main(String[] args) {
        System.out.println("Running InlineProjectConstants test...");
        runTest();
    }
}
