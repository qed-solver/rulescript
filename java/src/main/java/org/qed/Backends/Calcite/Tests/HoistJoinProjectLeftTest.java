package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.HoistJoinProjectLeft;

import java.util.List;

public class HoistJoinProjectLeftTest {
    public static void runTest() {
        var rule = new HoistJoinProjectLeft();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var schema = RuleBuilder.create();
        var leftTable = RuleChecks.table(schema, 2, false);
        var rightTable = RuleChecks.table(schema, 1, false);
        var b = schema.transform(CalciteRuntime::configure);
        var rex = b.getRexBuilder();
        b.scan(leftTable);
        var left = b.peek();
        b.project(List.of(rex.makeInputRef(left, 1), rex.makeInputRef(left, 0)));
        b.scan(rightTable);
        var projectedLeftJoin = b.peek(1);
        var condition = RuleChecks.eq(b,
                rex.makeInputRef(projectedLeftJoin.getRowType().getFieldList().get(0).getType(), 0),
                rex.makeInputRef(projectedLeftJoin.getRowType().getFieldList().get(0).getType(), 2));
        b.join(JoinRelType.INNER, condition);
        RelNode input = b.build();

        var expectedBuilder = schema.transform(CalciteRuntime::configure);
        expectedBuilder.scan(leftTable).scan(rightTable);
        var rawJoin = expectedBuilder.peek(1);
        var expectedCondition = RuleChecks.eq(expectedBuilder,
                expectedBuilder.getRexBuilder().makeInputRef(rawJoin.getRowType().getFieldList().get(0).getType(), 1),
                expectedBuilder.getRexBuilder().makeInputRef(rawJoin.getRowType().getFieldList().get(0).getType(), 2));
        expectedBuilder.join(JoinRelType.INNER, expectedCondition);
        var expectedJoin = expectedBuilder.build();
        RelNode expected = LogicalProject.create(expectedJoin, List.of(), List.of(
                expectedBuilder.getRexBuilder().makeInputRef(expectedJoin, 1),
                expectedBuilder.getRexBuilder().makeInputRef(expectedJoin, 0),
                expectedBuilder.getRexBuilder().makeInputRef(expectedJoin, 2)), (List<String>) null);
        RuleChecks.rewrites(rule, input, expected);

        var noMatch = RuleBuilder.create();
        var noMatchLeftTable = RuleChecks.table(noMatch, 2, false);
        var noMatchRightTable = RuleChecks.table(noMatch, 1, false);
        var n = noMatch.transform(CalciteRuntime::configure);
        n.scan(noMatchLeftTable);
        var rawNoMatchLeft = n.peek();
        n.project(List.of(n.getRexBuilder().makeInputRef(rawNoMatchLeft, 0),
                n.getRexBuilder().makeInputRef(rawNoMatchLeft, 1)));
        n.scan(noMatchRightTable);
        var identityProjectedLeft = n.peek(1);
        var noMatchCondition = RuleChecks.eq(n,
                n.getRexBuilder().makeInputRef(identityProjectedLeft.getRowType().getFieldList().get(0).getType(), 0),
                n.getRexBuilder().makeInputRef(identityProjectedLeft.getRowType().getFieldList().get(0).getType(), 2));
        n.join(JoinRelType.INNER, noMatchCondition);
        RuleChecks.doesNotApply(rule, n.build());
    }

    public static void main(String[] args) {
        System.out.println("Running HoistJoinProjectLeft test...");
        runTest();
    }
}
