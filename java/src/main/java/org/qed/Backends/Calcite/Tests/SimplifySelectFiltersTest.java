package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.qed.RRuleInstances.SimplifySelectFilters;
import org.qed.RuleBuilder;

public class SimplifySelectFiltersTest {

    public static void runTest() {
        var rule = new SimplifySelectFilters();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        orNullConjunct(rule);
    }

    private static void orNullConjunct(SimplifySelectFilters rule) {
        var base = RuleBuilder.create();
        var table = RuleChecks.table(base, 1, false);
        var five = base.literal(5);
        var ten = base.literal(10);
        var plain = RuleChecks.ruleBuilder(base);
        var nullBool = plain.getRexBuilder()
                .makeNullLiteral(plain.getTypeFactory().createSqlType(SqlTypeName.BOOLEAN));
        var before = plain.scan(table)
                .filter(plain.call(SqlStdOperatorTable.OR,
                        RuleChecks.gt(plain, plain.field(0), 5), nullBool))
                .build();
        var after = plain.scan(table).filter(RuleChecks.gt(plain, plain.field(0), 5)).build();
        RuleChecks.rewrites(rule, before, after);

        var noNull = base.scan(table)
                .filter(base.call(SqlStdOperatorTable.OR,
                        base.call(SqlStdOperatorTable.GREATER_THAN, base.field(0), five),
                        base.call(SqlStdOperatorTable.LESS_THAN, base.field(0), ten)))
                .build();
        RuleChecks.doesNotApply(rule, noNull);
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifySelectFilters test...");
        runTest();
    }
}
