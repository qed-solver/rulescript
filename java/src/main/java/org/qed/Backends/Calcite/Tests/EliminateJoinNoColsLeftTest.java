package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteRuntime;
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
        var raw = builder.transform(config -> CalciteRuntime.configure(config).withSimplifyValues(false));
        var before = raw.values(new String[]{"_r"}, true).project(List.of())
                .scan(r)
                .join(JoinRelType.INNER, RuleChecks.gt(raw, raw.field(0), 5))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var after = plain.scan(r)
                .filter(RuleChecks.gt(plain, plain.field(0), 5))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var multiRow = raw.values(new String[]{"_r"}, true, false).project(List.of())
                .scan(r)
                .join(JoinRelType.INNER, RuleChecks.gt(raw, raw.field(0), 5))
                .build();
        RuleChecks.doesNotApply(rule, multiRow);

        var leftOuter = raw.values(new String[]{"_r"}, true).project(List.of())
                .scan(r)
                .join(JoinRelType.LEFT, RuleChecks.gt(raw, raw.field(0), 5))
                .build();
        RuleChecks.doesNotApply(rule, leftOuter);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinNoColsLeft test...");
        runTest();
    }
}
