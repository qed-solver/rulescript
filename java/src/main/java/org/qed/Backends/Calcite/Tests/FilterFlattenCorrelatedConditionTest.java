package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FilterFlattenCorrelatedCondition;
import org.qed.RuleBuilder;

import java.util.List;

public class FilterFlattenCorrelatedConditionTest {
    public static void runTest() {
        var rule = new FilterFlattenCorrelatedCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesCorrelatedComputedComparison();
        rejectsUncorrelatedComparison();
    }

    private static void rewritesCorrelatedComputedComparison() {
        var rule = new FilterFlattenCorrelatedCondition();
        var builder = RuleBuilder.create();
        var outerName = RuleChecks.table(builder, 1, false);
        var innerName = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(outerName).scan(innerName).join(org.apache.calcite.rel.core.JoinRelType.INNER, b.literal(true));
        var before = b.filter(RuleChecks.eq(b, b.field(0),
                b.call(SqlStdOperatorTable.PLUS, b.field(1), b.literal(1)))).build();
        b.scan(outerName).scan(innerName).join(org.apache.calcite.rel.core.JoinRelType.INNER, b.literal(true));
        var expected = b.project(b.field(0), b.field(1),
                b.call(SqlStdOperatorTable.PLUS, b.field(1), b.literal(1)))
                .filter(RuleChecks.eq(b, b.field(0), b.field(2)))
                .project(b.field(0), b.field(1)).build();
        RuleChecks.rewrites(rule, before, expected);
    }

    private static void rejectsUncorrelatedComparison() {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var b = RuleChecks.ruleBuilder(builder);
        b.scan(table);
        var value = b.field(0);
        var expr = b.call(SqlStdOperatorTable.PLUS, value, b.literal(1));
        b.filter(RuleChecks.eq(b, value, expr));
        var plan = b.build();
        RuleChecks.doesNotApply(new FilterFlattenCorrelatedCondition(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running FilterFlattenCorrelatedCondition test...");
        runTest();
    }
}
