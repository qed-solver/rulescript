package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNullComparisonLeft;
import org.qed.RuleBuilder;

public class FoldNullComparisonLeftTest {

    public static void runTest() {
        var rule = new FoldNullComparisonLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nullOnLeft(rule);
    }

    private static void nullOnLeft(FoldNullComparisonLeft rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var rex = plain.getRexBuilder();
        var nullInt = rex.makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var nullBool = rex.makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var scan = plain.scan(table).build();
        var before = LogicalFilter.create(scan,
                rex.makeCall(SqlStdOperatorTable.EQUALS, nullInt, rex.makeInputRef(scan, 0)));
        var after = LogicalFilter.create(scan, nullBool);
        RuleChecks.rewrites(rule, before, after);

        var five = builder.literal(5);
        var columnEqualsFive = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.EQUALS, builder.field(0), five))
                .build();
        RuleChecks.doesNotApply(rule, columnEqualsFive);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullComparisonLeft test...");
        runTest();
    }
}
