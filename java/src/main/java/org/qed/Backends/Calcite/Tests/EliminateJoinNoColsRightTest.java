package org.qed.Backends.Calcite.Tests;

import java.util.List;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.RRuleInstances.EliminateJoinNoColsRight;
import org.qed.RuleBuilder;

public class EliminateJoinNoColsRightTest {

    public static void runTest() {
        var rule = new EliminateJoinNoColsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        oneRowZeroColumnRight(rule);
    }

    private static void oneRowZeroColumnRight(EliminateJoinNoColsRight rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var before = plain.scan(table)
                .values(new String[]{"_r"}, true)
                .project(List.of(), List.of(), true)
                .join(JoinRelType.INNER, RuleChecks.gt(plain, plain.field(2, 0, 0), 5))
                .build();
        var after = plain.scan(table)
                .filter(RuleChecks.gt(plain, plain.field(0), 5))
                .build();
        RuleChecks.rewrites(rule, before, after);

        var manyRows = plain.scan(table)
                .values(new String[]{"_r"}, true, false)
                .project(List.of(), List.of(), true)
                .join(JoinRelType.INNER, RuleChecks.gt(plain, plain.field(2, 0, 0), 5))
                .build();
        RuleChecks.doesNotApply(rule, manyRows);

        var leftJoin = plain.scan(table)
                .values(new String[]{"_r"}, true)
                .project(List.of(), List.of(), true)
                .join(JoinRelType.LEFT, RuleChecks.gt(plain, plain.field(2, 0, 0), 5))
                .build();
        RuleChecks.doesNotApply(rule, leftJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateJoinNoColsRight test...");
        runTest();
    }
}
