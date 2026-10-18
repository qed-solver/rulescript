package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyNotInSingleElement;
import org.qed.RuleBuilder;

public final class SimplifyNotInSingleElementTest {
    public static void runTest() {
        var rule = new SimplifyNotInSingleElement();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var tableName = RuleChecks.table(builder, 2, false);
        var source = builder.scan(tableName).build();
        var rb = source.getCluster().getRexBuilder();
        var left = rb.makeInputRef(source.getRowType().getFieldList().get(0).getType(), 0);
        var right = rb.makeInputRef(source.getRowType().getFieldList().get(1).getType(), 1);
        var equals = rb.makeCall(SqlStdOperatorTable.EQUALS, left, right);
        var disjunction = rb.makeCall(SqlStdOperatorTable.OR, java.util.List.of(equals));
        var condition = rb.makeCall(SqlStdOperatorTable.NOT, disjunction);
        var input = org.apache.calcite.rel.logical.LogicalFilter.create(source, condition);

        var expected = org.apache.calcite.rel.logical.LogicalFilter.create(source,
                rb.makeCall(SqlStdOperatorTable.NOT_EQUALS, left, right));
        RuleChecks.rewrites(rule, input, expected);

        var nearCondition = rb.makeCall(SqlStdOperatorTable.NOT,
                rb.makeCall(SqlStdOperatorTable.NOT_EQUALS, left, right));
        RuleChecks.doesNotApply(rule, org.apache.calcite.rel.logical.LogicalFilter.create(source, nearCondition));
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyNotInSingleElement test...");
        runTest();
    }
}
