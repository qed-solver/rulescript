package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalIntersect;
import org.apache.calcite.rel.logical.LogicalUnion;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRuleInstances.PushFilterIntoSetOp;
import org.qed.RuleBuilder;

public class PushFilterIntoSetOpTest {
    public static void main(String[] args) {
        System.out.println("Running PushFilterIntoSetOp test...");
        runTest();
    }

    public static void runTest() {
        var rule = new PushFilterIntoSetOp();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushFilterIntoSetOp rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false, false);
        var r = RuleChecks.table(builder, 1, false, false);
        var inputs = java.util.List.of(builder.scan(l).build(), builder.scan(r).build());
        var intersection = LogicalIntersect.create(inputs, false);
        var condition = builder.call(SqlStdOperatorTable.IS_NOT_NULL, builder.getRexBuilder().makeInputRef(intersection, 0));
        var plan = LogicalFilter.create(intersection, condition);

        var plain = RuleChecks.ruleBuilder(builder);
        var expectedLeft = plain.scan(l).filter(plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(0))).build();
        var expectedRight = plain.scan(r).filter(plain.call(SqlStdOperatorTable.IS_NOT_NULL, plain.field(0))).build();
        var expected = LogicalIntersect.create(java.util.List.of(expectedLeft, expectedRight), false);
        RuleChecks.rewrites(rule, plan, expected);

        var union = LogicalUnion.create(inputs, false);
        var nearMiss = LogicalFilter.create(union, condition);
        RuleChecks.doesNotApply(rule, nearMiss);
    }
}
