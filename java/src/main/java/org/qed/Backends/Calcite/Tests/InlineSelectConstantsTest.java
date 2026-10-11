package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.InlineSelectConstants;
import org.qed.RuleBuilder;

public class InlineSelectConstantsTest {

    public static void runTest() {
        var rule = new InlineSelectConstants();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(InlineSelectConstants rule) {
        var builder = RuleBuilder.create();
        var source = RuleChecks.table(builder, 1, false);
        var constant = RuleChecks.table(builder, 1, true, false);
        var predicate = builder.genericPredicateOp("g", true);
        var before = builder.scan(source).scan(constant)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.call(predicate, builder.field(0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(source).scan(constant)
                .join(JoinRelType.INNER, RuleChecks.eq(plain, plain.field(2, 0, 0), plain.field(2, 1, 0)))
                .filter(plain.call(predicate, plain.field(1)))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var nonUnique = RuleChecks.table(builder, 1, false);
        RuleChecks.doesNotApply(rule, builder.scan(source).scan(nonUnique)
                .join(JoinRelType.INNER, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .filter(builder.call(predicate, builder.field(0)))
                .build());
    }

    public static void main(String[] args) {
        System.out.println("Running InlineSelectConstants test...");
        runTest();
    }
}
