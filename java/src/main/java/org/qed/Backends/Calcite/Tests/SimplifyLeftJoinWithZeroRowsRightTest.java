package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.SimplifyLeftJoinWithZeroRowsRight;
import org.qed.RuleBuilder;

public class SimplifyLeftJoinWithZeroRowsRightTest {

    public static void runTest() {
        var rule = new SimplifyLeftJoinWithZeroRowsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        emptyRightSide(rule);
    }

    private static void emptyRightSide(SimplifyLeftJoinWithZeroRowsRight rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 2, false);
        var r = RuleChecks.table(builder, 2, false);
        var before = builder.scan(l).scan(r).empty()
                .join(JoinRelType.LEFT, builder.literal(true)).build();
        var plain = RuleChecks.ruleBuilder(builder);
        var nullInt = plain.getRexBuilder().makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var after = plain.scan(l).project(plain.field(0), plain.field(1), nullInt, nullInt).build();
        RuleChecks.rewrites(rule, before, after);

        var nonEmpty = builder.scan(l).scan(r)
                .join(JoinRelType.LEFT, builder.literal(true)).build();
        RuleChecks.doesNotApply(rule, nonEmpty);

        var innerJoin = builder.scan(l).scan(r).empty()
                .join(JoinRelType.INNER, builder.literal(true)).build();
        RuleChecks.doesNotApply(rule, innerJoin);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyLeftJoinWithZeroRowsRight test...");
        runTest();
    }
}
