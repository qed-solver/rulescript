package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.FoldNonNullTupleIsTupleNotNull;
import org.qed.RuleBuilder;

public class FoldNonNullTupleIsTupleNotNullTest {

    public static void runTest() {
        var rule = new FoldNonNullTupleIsTupleNotNull();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        nonNullColumns(rule);
    }

    private static void nonNullColumns(FoldNonNullTupleIsTupleNotNull rule) {
        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 2, false, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var notNull0 = plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(0));
        var notNull1 = plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(1));
        var condition = plain.getRexBuilder().makeCall(SqlStdOperatorTable.AND, notNull0, notNull1);
        var before = LogicalFilter.create(plain.scan(table).build(), condition);
        var after = plain.scan(table).filter(plain.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        var oneNull = LogicalFilter.create(plain.scan(table).build(), notNull0);
        RuleChecks.doesNotApply(rule, oneNull);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNonNullTupleIsTupleNotNull test...");
        runTest();
    }
}
