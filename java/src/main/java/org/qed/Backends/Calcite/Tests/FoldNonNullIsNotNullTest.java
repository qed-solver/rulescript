package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNonNullIsNotNull;
import org.qed.RuleBuilder;

public class FoldNonNullIsNotNullTest {

    public static void runTest() {
        var rule = new FoldNonNullIsNotNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nonNullColumns(rule);
    }

    private static void nonNullColumns(FoldNonNullIsNotNull rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        var nullableTable = RuleChecks.table(builder, 1, false, true);
        var plain = RuleChecks.ruleBuilder(builder);
        var source = plain.scan(table).build();
        var rex = plain.getRexBuilder();
        var before = LogicalFilter.create(source,
                rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, rex.makeInputRef(source, 0)));
        var after = plain.scan(table).filter(plain.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        var nullable = plain.scan(nullableTable)
                .filter(plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(0)))
                .build();
        RuleChecks.doesNotApply(rule, nullable);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNonNullIsNotNull test...");
        runTest();
    }
}
