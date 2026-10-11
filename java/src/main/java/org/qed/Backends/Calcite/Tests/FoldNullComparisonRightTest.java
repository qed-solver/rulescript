package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNullComparisonRight;
import org.qed.RuleBuilder;

public class FoldNullComparisonRightTest {

    public static void runTest() {
        var rule = new FoldNullComparisonRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nullRightOperand(rule);
    }

    private static void nullRightOperand(FoldNullComparisonRight rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var nullBool = plain.getRexBuilder()
                .makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var input = plain.scan(table).build();
        var column = plain.getRexBuilder().makeInputRef(input, 0);
        var before = LogicalFilter.create(input,
                plain.call(SqlStdOperatorTable.EQUALS, column, nullBool));
        var after = plain.scan(table).filter(nullBool).build();
        RuleChecks.rewrites(rule, before, after);

        var nonNullRight = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), builder.literal(5)))
                .build();
        RuleChecks.doesNotApply(rule, nonNullRight);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullComparisonRight test...");
        runTest();
    }
}
