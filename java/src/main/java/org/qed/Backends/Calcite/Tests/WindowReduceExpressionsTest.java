package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.WindowReduceExpressions;
import org.qed.RuleBuilder;

public final class WindowReduceExpressionsTest {
    public static void runTest() {
        var rule = new WindowReduceExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        handBuiltPlan(rule);
        rejectsWrongJoinType();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("WindowReduceExpressions had " + CalciteTester.failures() + " failure(s)");
        }
    }

    private static void handBuiltPlan(WindowReduceExpressions rule) {
        var qb = RuleBuilder.create();
        var table = RuleChecks.table(qb, 2, false);
        var scanBuilder = RuleChecks.ruleBuilder(qb);
        RelNode source = scanBuilder.scan(table).build();
        var b = RuleChecks.ruleBuilder(qb);
        b.push(source);
        RexNode falseAndPredicate = b.getRexBuilder().makeCall(SqlStdOperatorTable.AND,
                b.getRexBuilder().makeLiteral(false), b.getRexBuilder().makeCall(SqlStdOperatorTable.IS_NOT_NULL,
                        b.field(1)));
        RelNode projected = b.project(b.field(0), falseAndPredicate).build();
        var aggregateFunction = qb.genericAggregateOp("f", org.qed.RelType.fromString("INTEGER", true));
        var aggBuilder = RuleChecks.ruleBuilder(qb);
        RelNode aggregate = aggBuilder.push(projected).aggregate(aggBuilder.groupKey(aggBuilder.field(0)),
                aggBuilder.aggregateCall(aggregateFunction, false, null, "f", aggBuilder.field(1))).build();
        var joinBuilder = RuleChecks.ruleBuilder(qb);
        RelNode before = joinBuilder.push(source).push(aggregate)
                .join(JoinRelType.INNER, RuleChecks.eq(joinBuilder,
                        joinBuilder.field(2, 0, 0), joinBuilder.field(2, 1, 0))).build();

        // Independently build the expected output by retaining the key and replacing only the expression with FALSE.
        var expectedBuilder = RuleChecks.ruleBuilder(qb);
        RelNode expectedProject = expectedBuilder.push(source).project(expectedBuilder.field(0),
                expectedBuilder.getRexBuilder().makeLiteral(false)).build();
        var expectedAggregateBuilder = RuleChecks.ruleBuilder(qb);
        RelNode emptyAggregate = expectedAggregateBuilder.push(expectedProject)
                .aggregate(expectedAggregateBuilder.groupKey(expectedAggregateBuilder.field(0)),
                        expectedAggregateBuilder.aggregateCall(aggregateFunction, false, null, "f",
                                expectedAggregateBuilder.field(1))).build();
        var expectedJoinBuilder = RuleChecks.ruleBuilder(qb);
        RelNode after = expectedJoinBuilder.push(source).push(emptyAggregate)
                .join(JoinRelType.INNER, RuleChecks.eq(expectedJoinBuilder,
                        expectedJoinBuilder.field(2, 0, 0), expectedJoinBuilder.field(2, 1, 0))).build();
        RuleChecks.rewrites(rule, before, after);
    }

    private static void rejectsWrongJoinType() {
        var qb = RuleBuilder.create();
        var table = RuleChecks.table(qb, 2, false);
        var scanBuilder = RuleChecks.ruleBuilder(qb);
        var scan = scanBuilder.scan(table).build();
        var aggregateBuilder = RuleChecks.ruleBuilder(qb);
        var aggregate = aggregateBuilder.push(scan).aggregate(aggregateBuilder.groupKey(aggregateBuilder.field(0)),
                aggregateBuilder.count(aggregateBuilder.field(1))).build();
        var joinBuilder = RuleChecks.ruleBuilder(qb);
        var left = joinBuilder.push(scan).push(aggregate)
                .join(JoinRelType.LEFT, RuleChecks.eq(joinBuilder,
                        joinBuilder.field(2, 0, 0), joinBuilder.field(2, 1, 0))).build();
        RuleChecks.doesNotApply(new WindowReduceExpressions(), left);
    }

    public static void main(String[] args) {
        System.out.println("Running WindowReduceExpressions test...");
        runTest();
    }
}
