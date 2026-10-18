package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.RejectNullsLeftJoin;

public class RejectNullsLeftJoinTest {
    public static void runTest() {
        var rule = new RejectNullsLeftJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(RejectNullsLeftJoin rule) {
        var builder = RuleBuilder.create();
        var l = RuleChecks.table(builder, 1, false);
        var r = RuleChecks.table(builder, 1, false);
        builder.scan(l).scan(r);
        var on = builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(2, 0, 0), builder.field(2, 1, 0));
        var leftJoin = builder.join(JoinRelType.LEFT, on).build();
        var condition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                builder.getRexBuilder().makeInputRef(leftJoin.getRowType().getFieldList().get(1).getType(), 1));
        RelNode plan = LogicalFilter.create(leftJoin, condition);

        RelBuilder plain = RuleChecks.ruleBuilder(builder);
        var innerJoin = plain.scan(l).scan(r).join(JoinRelType.INNER,
                plain.call(SqlStdOperatorTable.LESS_THAN, plain.field(2, 0, 0), plain.field(2, 1, 0))).build();
        RexNode expectedCondition = plain.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                plain.getRexBuilder().makeInputRef(innerJoin.getRowType().getFieldList().get(1).getType(), 1));
        RelNode expected = LogicalFilter.create(innerJoin, expectedCondition);
        RuleChecks.rewrites(rule, plan, expected);

        var fullJoin = builder.scan(l).scan(r).join(JoinRelType.FULL,
                builder.call(SqlStdOperatorTable.LESS_THAN, builder.field(2, 0, 0), builder.field(2, 1, 0))).build();
        var fullCondition = builder.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                builder.getRexBuilder().makeInputRef(fullJoin.getRowType().getFieldList().get(1).getType(), 1));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(fullJoin, fullCondition));
    }

    public static void main(String[] args) {
        System.out.println("Running RejectNullsLeftJoin test...");
        runTest();
    }
}
