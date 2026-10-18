package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNotNull;
import org.qed.RuleBuilder;

public class FoldNotNullTest {
    public static void runTest() {
        var rule = new FoldNotNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPlan();
        rejectsDifferentConstant();
    }

    private static void rewritesHandBuiltPlan() {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var nullBoolean = builder.getRexBuilder().makeNullLiteral(
                builder.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var before = builder.scan(table).filter(builder.not(nullBoolean)).build();

        var expectedRuleBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedRuleBuilder, 1, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedRuleBuilder);
        var expectedNullBoolean = expectedBuilder.getRexBuilder().makeNullLiteral(
                expectedBuilder.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var expected = expectedBuilder.scan(expectedTable).filter(expectedNullBoolean).build();
        RuleChecks.rewrites(new FoldNotNull(), before, expected);
    }

    private static void rejectsDifferentConstant() {
        var ruleBuilder = RuleBuilder.create();
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var before = builder.scan(table).filter(builder.not(builder.literal(true))).build();

        var expectedRuleBuilder = RuleBuilder.create();
        var expectedTable = RuleChecks.table(expectedRuleBuilder, 1, false);
        var expectedBuilder = RuleChecks.ruleBuilder(expectedRuleBuilder);
        var expected = expectedBuilder.scan(expectedTable)
                .filter(expectedBuilder.not(expectedBuilder.literal(true))).build();
        if (!before.explain().equals(expected.explain())) {
            throw new AssertionError("the separately built no-match plan differs");
        }
        RuleChecks.doesNotApply(new FoldNotNull(), before);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNotNull test...");
        runTest();
    }
}
