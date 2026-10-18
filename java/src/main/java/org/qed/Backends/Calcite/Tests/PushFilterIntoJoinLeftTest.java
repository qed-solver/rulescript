package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.RelBuilder;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.PushFilterIntoJoinLeft;
import org.qed.RuleBuilder;

public class PushFilterIntoJoinLeftTest {
    public static void main(String[] args) {
        System.out.println("Running PushFilterIntoJoinLeft test...");
        runTest();
    }

    public static void runTest() {
        var rule = new PushFilterIntoJoinLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushFilterIntoJoinLeft rule) {
        var builder = RuleBuilder.create();
        builder.transform(CalciteRuntime::configure);
        var l = RuleChecks.table(builder, 1, false, false);
        var r = RuleChecks.table(builder, 1, false, false);
        SqlOperator f = new RuleBuilder.QedFunction("leftBound", new org.qed.RelType.BaseType(SqlTypeName.BOOLEAN, false));
        SqlOperator g = new RuleBuilder.QedFunction("unbound", new org.qed.RelType.BaseType(SqlTypeName.BOOLEAN, false));
        var left = builder.scan(l).build();
        var right = builder.scan(r).build();
        var joinCondition = builder.call(SqlStdOperatorTable.AND,
                builder.call(f, builder.getRexBuilder().makeInputRef(left, 0)),
                builder.call(g, builder.getRexBuilder().makeInputRef(left, 0),
                        builder.getRexBuilder().makeInputRef(right, 0)));
        var plan = org.apache.calcite.rel.logical.LogicalJoin.create(left, right, java.util.List.of(),
                joinCondition, com.google.common.collect.ImmutableSet.of(), JoinRelType.INNER);
        var plain = RuleChecks.ruleBuilder(builder);
        var expectedLeft = org.apache.calcite.rel.logical.LogicalFilter.create(left,
                plain.call(f, plain.getRexBuilder().makeInputRef(left, 0)));
        var expectedCondition = plain.call(g, plain.getRexBuilder().makeInputRef(expectedLeft, 0),
                plain.getRexBuilder().makeInputRef(right, 0));
        var expected = org.apache.calcite.rel.logical.LogicalJoin.create(expectedLeft, right, java.util.List.of(),
                expectedCondition, com.google.common.collect.ImmutableSet.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, plan, expected);

        var leftJoin = org.apache.calcite.rel.logical.LogicalJoin.create(left, right, java.util.List.of(),
                joinCondition, com.google.common.collect.ImmutableSet.of(), JoinRelType.LEFT);
        RuleChecks.doesNotApply(rule, leftJoin);
    }
}
