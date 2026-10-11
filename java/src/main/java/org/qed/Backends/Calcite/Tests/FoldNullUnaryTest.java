package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNullUnary;
import org.qed.RuleBuilder;

public class FoldNullUnaryTest {

    public static void runTest() {
        var rule = new FoldNullUnary();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nullOperand(rule);
    }

    private static void nullOperand(FoldNullUnary rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var nullInt = builder.getRexBuilder()
                .makeNullLiteral(builder.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var before = builder.scan(table)
                .project(builder.call(SqlStdOperatorTable.UNARY_MINUS, nullInt))
                .build();
        var plain = RuleChecks.ruleBuilder(builder);
        var nullResult = plain.getRexBuilder()
                .makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var after = plain.scan(table).project(nullResult).build();
        RuleChecks.rewrites(rule, before, after);

        var negatedColumn = builder.scan(table)
                .project(builder.call(SqlStdOperatorTable.UNARY_MINUS, builder.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, negatedColumn);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullUnary test...");
        runTest();
    }
}
