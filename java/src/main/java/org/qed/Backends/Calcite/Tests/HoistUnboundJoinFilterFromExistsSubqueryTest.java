package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.HoistUnboundJoinFilterFromExistsSubquery;
import org.qed.RuleBuilder;

public final class HoistUnboundJoinFilterFromExistsSubqueryTest {
    public static void runTest() {
        var rule = new HoistUnboundJoinFilterFromExistsSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var outerName = RuleChecks.table(builder, 1, false);
        var leftName = RuleChecks.table(builder, 1, false);
        var rightName = RuleChecks.table(builder, 1, false);
        var b = builder.transform(CalciteRuntime::configure);
        RelNode outer = b.scan(outerName).build();
        RelNode left = b.scan(leftName).build();
        RelNode right = b.scan(rightName).build();
        var rex = b.getRexBuilder();
        RexNode outerBound = rex.makeCall(SqlStdOperatorTable.CURRENT_USER);
        RexNode leftRef = rex.makeInputRef(left, 0);
        RexNode rightRef = rex.makeInputRef(right, 0);
        RexNode joinPred = rex.makeCall(SqlStdOperatorTable.EQUALS, leftRef, rightRef);
        RexNode unboundCond = rex.makeCall(SqlStdOperatorTable.CURRENT_USER);
        RexNode joinCondition = rex.makeCall(SqlStdOperatorTable.AND, unboundCond, joinPred);
        RelNode join = LogicalJoin.create(left, right, java.util.List.of(), joinCondition,
                java.util.Set.of(), JoinRelType.INNER);
        RelNode before = LogicalFilter.create(outer, RexSubQuery.exists(join));
        RelNode expectedJoin = LogicalJoin.create(left, right, java.util.List.of(), joinPred,
                java.util.Set.of(), JoinRelType.INNER);
        RelNode after = LogicalFilter.create(outer,
                rex.makeCall(SqlStdOperatorTable.AND, RexSubQuery.exists(expectedJoin), outerBound));
        RuleChecks.rewrites(rule, before, after);
        RuleChecks.doesNotApply(rule, LogicalFilter.create(outer,
                rex.makeCall(SqlStdOperatorTable.AND, RexSubQuery.exists(expectedJoin), outerBound)));
    }

    public static void main(String[] args) {
        System.out.println("Running HoistUnboundJoinFilterFromExistsSubquery test...");
        runTest();
    }
}
