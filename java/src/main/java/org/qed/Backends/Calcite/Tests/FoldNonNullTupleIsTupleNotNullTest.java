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
        var rex = plain.getRexBuilder();
        var source = plain.scan(table).build();
        var notNull0 = rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, rex.makeInputRef(source, 0));
        var notNull1 = rex.makeCall(SqlStdOperatorTable.IS_NOT_NULL, rex.makeInputRef(source, 1));
        var condition = rex.makeCall(SqlStdOperatorTable.AND, notNull0, notNull1);
        var before = LogicalFilter.create(source, condition);
        var after = plain.push(source).filter(plain.literal(true)).build();
        RuleChecks.rewrites(rule, before, after);

        var oneNull = LogicalFilter.create(source, notNull0);
        RuleChecks.doesNotApply(rule, oneNull);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldNonNullTupleIsTupleNotNull test...");
        runTest();
    }
}
