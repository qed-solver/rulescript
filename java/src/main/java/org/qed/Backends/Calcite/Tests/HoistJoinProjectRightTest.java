package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalProject;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.HoistJoinProjectRight;
import org.qed.RuleBuilder;

import java.util.List;

public class HoistJoinProjectRightTest {
    public static void runTest() {
        var rule = new HoistJoinProjectRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var schema = RuleBuilder.create();
        var leftTable = RuleChecks.table(schema, 1, false);
        var rightTable = RuleChecks.table(schema, 2, false);
        var b = schema.transform(CalciteRuntime::configure);
        var left = b.scan(leftTable).build();
        var right = b.scan(rightTable).build();
        b.push(left).push(right);
        b.project(List.of(b.getRexBuilder().makeInputRef(right, 1),
                b.getRexBuilder().makeInputRef(right, 0)));
        var projectedJoin = b.peek(1);
        var condition = RuleChecks.eq(b,
                b.getRexBuilder().makeInputRef(projectedJoin.getRowType().getFieldList().get(0).getType(), 0),
                b.getRexBuilder().makeInputRef(projectedJoin.getRowType().getFieldList().get(0).getType(), 1));
        b.join(JoinRelType.LEFT, condition);
        RelNode input = b.build();

        var expectedBuilder = schema.transform(CalciteRuntime::configure);
        var rawLeft = expectedBuilder.scan(leftTable).build();
        var rawRight = expectedBuilder.scan(rightTable).build();
        expectedBuilder.push(rawLeft).push(rawRight);
        var rawJoin = expectedBuilder.peek(1);
        var expectedCondition = RuleChecks.eq(expectedBuilder,
                expectedBuilder.getRexBuilder().makeInputRef(rawJoin.getRowType().getFieldList().get(0).getType(), 0),
                expectedBuilder.getRexBuilder().makeInputRef(rawJoin.getRowType().getFieldList().get(0).getType(), 2));
        expectedBuilder.join(JoinRelType.LEFT, expectedCondition);
        var expectedJoin = expectedBuilder.build();
        RelNode expected = LogicalProject.create(expectedJoin, List.of(), List.of(
                expectedBuilder.getRexBuilder().makeInputRef(expectedJoin, 0),
                expectedBuilder.getRexBuilder().makeInputRef(expectedJoin, 2),
                expectedBuilder.getRexBuilder().makeInputRef(expectedJoin, 1)), (List<String>) null);
        RuleChecks.rewrites(rule, input, expected);

        var noMatch = RuleBuilder.create();
        var noMatchLeftTable = RuleChecks.table(noMatch, 1, false);
        var noMatchRightTable = RuleChecks.table(noMatch, 2, false);
        var n = noMatch.transform(CalciteRuntime::configure);
        var rawNoMatchLeft = n.scan(noMatchLeftTable).build();
        var rawNoMatchRight = n.scan(noMatchRightTable).build();
        n.push(rawNoMatchLeft).push(rawNoMatchRight);
        n.project(List.of(n.getRexBuilder().makeInputRef(rawNoMatchRight, 0),
                n.getRexBuilder().makeInputRef(rawNoMatchRight, 1)));
        var identityProjected = n.peek(1);
        var noMatchCondition = RuleChecks.eq(n,
                n.getRexBuilder().makeInputRef(identityProjected.getRowType().getFieldList().get(0).getType(), 0),
                n.getRexBuilder().makeInputRef(identityProjected.getRowType().getFieldList().get(0).getType(), 1));
        n.join(JoinRelType.LEFT, noMatchCondition);
        RuleChecks.doesNotApply(rule, n.build());
    }

    public static void main(String[] args) {
        System.out.println("Running HoistJoinProjectRight test...");
        runTest();
    }
}
