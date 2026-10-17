package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyLeftJoinWithZeroRowsRight;
import org.qed.RuleBuilder;

public class SimplifyLeftJoinWithZeroRowsRightTest {
    public static void runTest() {
        var rule = new SimplifyLeftJoinWithZeroRowsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltEmptyRightJoin();
        meaningfulNoMatch();
    }

    private static void handBuiltEmptyRightJoin() {
        var builder = RuleBuilder.create();
        var leftName = RuleChecks.table(builder, 2, false, false);
        var rightName = RuleChecks.table(builder, 2, false, false);
        builder.scan(leftName).scan(rightName).empty();
        var before = builder.join(JoinRelType.LEFT, builder.literal(true)).build();
        var expectedBuilder = RuleBuilder.create();
        var input = RuleChecks.table(expectedBuilder, 2, false, false);
        var relBuilder = RuleChecks.ruleBuilder(expectedBuilder);
        var rex = relBuilder.getRexBuilder();
        var expected = relBuilder.scan(input).project(java.util.List.of(
                relBuilder.field(0), relBuilder.field(1),
                rex.makeNullLiteral(expectedBuilder.getTypeFactory().createSqlType(SqlTypeName.INTEGER)),
                rex.makeNullLiteral(expectedBuilder.getTypeFactory().createSqlType(SqlTypeName.INTEGER)))).build();
        RuleChecks.rewrites(new SimplifyLeftJoinWithZeroRowsRight(), before, expected);
    }

    private static void meaningfulNoMatch() {
        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 2, false);
        var right = RuleChecks.table(builder, 2, false);
        var plan = builder.scan(left).scan(right).join(JoinRelType.LEFT, builder.literal(true)).build();
        RuleChecks.doesNotApply(new SimplifyLeftJoinWithZeroRowsRight(), plan);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyLeftJoinWithZeroRowsRight test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("Calcite test failed");
        }
    }
}
