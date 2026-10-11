package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.FoldNullInNonEmpty;
import org.qed.RuleBuilder;

public class FoldNullInNonEmptyTest {

    public static void runTest() {
        var rule = new FoldNullInNonEmpty();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nullMembership(rule);
    }

    private static void nullMembership(FoldNullInNonEmpty rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var nullInt = plain.getRexBuilder()
                .makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.INTEGER));
        var before = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.OR,
                        builder.call(SqlStdOperatorTable.EQUALS, nullInt, builder.field(0)),
                        builder.call(SqlStdOperatorTable.EQUALS, nullInt, builder.field(1))))
                .build();
        var nullBool = plain.getRexBuilder()
                .makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var after = plain.scan(table).filter(nullBool).build();
        RuleChecks.rewrites(rule, before, after);

        var single = builder.scan(table)
                .filter(builder.call(SqlStdOperatorTable.EQUALS, nullInt, builder.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, single);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNullInNonEmpty test...");
        runTest();
    }
}
