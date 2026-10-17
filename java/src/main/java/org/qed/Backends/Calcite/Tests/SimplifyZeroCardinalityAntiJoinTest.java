package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyZeroCardinalityAntiJoin;
import org.qed.RuleBuilder;

public final class SimplifyZeroCardinalityAntiJoinTest {
    public static void runTest() {
        var rule = new SimplifyZeroCardinalityAntiJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        // Independent concrete witness: a one-row right input makes a TRUE anti join empty.
        var tables = RuleBuilder.create();
        var left = RuleChecks.table(tables, 1, false);
        var direct = tables.scan(left).push(rule.before().semantics().getInputs().get(1))
                .join(JoinRelType.ANTI, tables.literal(true)).build();
        var expectedBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedBuilder, 1, false);
        var empty = RuleChecks.ruleBuilder(expectedBuilder).scan(expectedTable).filter(
                expectedBuilder.getRexBuilder().makeLiteral(false)).build();
        RuleChecks.rewrites(rule, direct, empty);

        // An inner join does not satisfy the anti-join operator in the pattern.
        var other = RuleBuilder.create();
        var l = RuleChecks.table(other, 1, false);
        var r = RuleChecks.table(other, 1, false);
        var inner = other.scan(l).scan(r).join(JoinRelType.INNER, other.literal(true)).build();
        RuleChecks.doesNotApply(rule, inner);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyZeroCardinalityAntiJoin test...");
        runTest();
    }
}
