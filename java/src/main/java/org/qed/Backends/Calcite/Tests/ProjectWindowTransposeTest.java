package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.sql.SqlAggFunction;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.tools.RelBuilder;
import org.qed.RRule;
import org.qed.RRuleInstances.ProjectWindowTranspose;
import org.qed.RuleBuilder;

import java.util.List;

public final class ProjectWindowTransposeTest {
    public static void runTest() {
        System.out.println("Running RULE test...");
        RRule rule = new ProjectWindowTranspose();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var inputBuilder = RuleBuilder.create();
        var table = RuleChecks.table(inputBuilder, 3, false);
        var builder = RuleChecks.ruleBuilder(inputBuilder);
        builder.scan(table);
        var input = builder.build();

        builder.push(input);
        builder.project(List.of(builder.field(0), builder.field(1)));
        var projected = builder.build();

        builder.push(projected);
        var key = builder.groupKey(builder.field(0));
        var ops = RuleBuilder.create();
        SqlOperator top = ops.genericProjectionOp("Top", new org.qed.RelType.VarType("Top_Type", true));
        var f = (SqlAggFunction) ops.genericAggregateOp("f", new org.qed.RelType.VarType("F_Type", true));
        var g = (SqlAggFunction) ops.genericAggregateOp("g", new org.qed.RelType.VarType("G_Type", true));
        builder.aggregate(key, builder.aggregateCall(f, builder.field(1)), builder.aggregateCall(g, builder.field(1)));
        var aggregate = builder.build();

        builder.push(input);
        builder.push(aggregate);
        builder.join(JoinRelType.INNER, builder.getRexBuilder().makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                builder.getRexBuilder().makeInputRef(input.getRowType().getFieldList().get(0).getType(), 0),
                builder.getRexBuilder().makeInputRef(aggregate.getRowType().getFieldList().get(0).getType(), 3)));
        builder.project(List.of(builder.call(top, builder.field(0), builder.field(1), builder.field(4), builder.field(5))), List.of(), true);
        RelNode concreteBefore = builder.build();

        var expectedBuilder = RuleChecks.ruleBuilder(inputBuilder);
        expectedBuilder.push(input);
        expectedBuilder.project(List.of(expectedBuilder.field(0), expectedBuilder.field(1)));
        var reduced = expectedBuilder.build();
        expectedBuilder.push(reduced);
        expectedBuilder.aggregate(expectedBuilder.groupKey(expectedBuilder.field(0)),
                expectedBuilder.aggregateCall(f, expectedBuilder.field(1)),
                expectedBuilder.aggregateCall(g, expectedBuilder.field(1)));
        var expectedAggregate = expectedBuilder.build();
        expectedBuilder.push(reduced);
        expectedBuilder.push(expectedAggregate);
        expectedBuilder.join(JoinRelType.INNER,
                expectedBuilder.getRexBuilder().makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                expectedBuilder.getRexBuilder().makeInputRef(reduced.getRowType().getFieldList().get(0).getType(), 0),
                expectedBuilder.getRexBuilder().makeInputRef(expectedAggregate.getRowType().getFieldList().get(0).getType(), 2)));
        expectedBuilder.project(List.of(expectedBuilder.call(top, expectedBuilder.field(0), expectedBuilder.field(1),
                expectedBuilder.field(3), expectedBuilder.field(4))), List.of(), true);
        RelNode concreteAfter = expectedBuilder.build();
        RuleChecks.rewrites(rule, concreteBefore, concreteAfter);

        var nearInputBuilder = RuleBuilder.create();
        var nearTable = RuleChecks.table(nearInputBuilder, 3, false);
        var nearBuilder = RuleChecks.ruleBuilder(nearInputBuilder);
        nearBuilder.scan(nearTable);
        var nearInput = nearBuilder.build();
        nearBuilder.push(nearInput);
        nearBuilder.project(List.of(nearBuilder.field(0), nearBuilder.field(1)));
        var nearProjected = nearBuilder.build();
        nearBuilder.push(nearProjected);
        nearBuilder.aggregate(nearBuilder.groupKey(nearBuilder.field(1)),
                nearBuilder.aggregateCall(f, nearBuilder.field(0)),
                nearBuilder.aggregateCall(g, nearBuilder.field(0)));
        var nearAggregate = nearBuilder.build();
        nearBuilder.push(nearInput);
        nearBuilder.push(nearAggregate);
        nearBuilder.join(JoinRelType.INNER, nearBuilder.getRexBuilder().makeCall(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS,
                nearBuilder.getRexBuilder().makeInputRef(nearInput.getRowType().getFieldList().get(0).getType(), 0),
                nearBuilder.getRexBuilder().makeInputRef(nearAggregate.getRowType().getFieldList().get(0).getType(), 2)));
        nearBuilder.project(List.of(nearBuilder.call(top, nearBuilder.field(0), nearBuilder.field(1), nearBuilder.field(4), nearBuilder.field(5))), List.of(), true);
        RuleChecks.doesNotApply(rule, nearBuilder.build());
    }

    public static void main(String[] args) {
        runTest();
    }
}
