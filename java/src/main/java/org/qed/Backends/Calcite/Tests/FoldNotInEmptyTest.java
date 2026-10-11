package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.FoldNotInEmpty;
import org.qed.RuleBuilder;

public class FoldNotInEmptyTest {

    public static void runTest() {
        var rule = new FoldNotInEmpty();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(FoldNotInEmpty rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var before = builder.scan(table).scan(table).empty()
                .join(JoinRelType.ANTI, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(table).build();
        RuleChecks.rewrites(rule, before, after);

        var nonEmpty = builder.scan(table).scan(table)
                .join(JoinRelType.ANTI, RuleChecks.eq(builder, builder.field(2, 0, 0), builder.field(2, 1, 0)))
                .build();
        RuleChecks.doesNotApply(rule, nonEmpty);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNotInEmpty test...");
        runTest();
    }
}
