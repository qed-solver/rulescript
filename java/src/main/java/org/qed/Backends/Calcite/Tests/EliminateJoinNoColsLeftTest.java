package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.EliminateJoinNoColsLeft;
import org.qed.RuleBuilder;

import java.util.List;

public class EliminateJoinNoColsLeftTest {

    public static void runTest() {
        var rule = new EliminateJoinNoColsLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        oneRowZeroColumnLeft(rule);
    }

    private static void oneRowZeroColumnLeft(EliminateJoinNoColsLeft rule) {
        var builder = RuleBuilder.create();
        var r = RuleChecks.table(builder, 1, false);
        var before = builder.values(new String[]{"_r"}, true).project(List.of())
                .scan(r)
                .join(JoinRelType.INNER, RuleChecks.gt(builder, builder.field(0), 5))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(r)
                .filter(RuleChecks.gt(plain, plain.field(0), 5))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var multiRow = builder.values(new String[]{"_r"}, true, false).project(List.of())
                .scan(r)
                .join(JoinRelType.INNER, RuleChecks.gt(builder, builder.field(0), 5))
                .build();
        RuleChecks.doesNotApply(rule, multiRow);

        var leftOuter = builder.values(new String[]{"_r"}, true).project(List.of())
                .scan(r)
                .join(JoinRelType.LEFT, RuleChecks.gt(builder, builder.field(0), 5))
                .build();
        RuleChecks.doesNotApply(rule, leftOuter);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinNoColsLeft test...");
        runTest();
    }
}
