package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.JoinConditionPush;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

public class JoinConditionPushTest {
    public static void runTest() {
        var rule = new JoinConditionPush();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinConditionPush rule) {
        var qed = RuleBuilder.create();
        var leftTable = RuleChecks.table(qed, 1, false);
        var rightTable = RuleChecks.table(qed, 1, false);
        var leftOnly = qed.genericPredicateOp("left_conj", true);
        var rightOnly = qed.genericPredicateOp("right_conj", true);
        var cross = qed.genericPredicateOp("cross_conj", true);
        var b = RuleChecks.ruleBuilder(qed);
        var rex = b.getRexBuilder();
        var left = b.scan(leftTable).build();
        var right = b.scan(rightTable).build();
        var l = rex.makeInputRef(left, 0);
        var r = rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1);

        var condition = rex.makeCall(SqlStdOperatorTable.AND, rex.makeCall(leftOnly, l), rex.makeCall(rightOnly, r),
                rex.makeCall(cross, l, r));
        var before = LogicalJoin.create(left, right, List.of(), condition, Set.of(), JoinRelType.INNER);
        var pushedLeft = LogicalFilter.create(left, rex.makeCall(leftOnly, l));
        var pushedRight = LogicalFilter.create(right, rex.makeCall(rightOnly, rex.makeInputRef(right, 0)));
        var after = LogicalJoin.create(pushedLeft, pushedRight, List.of(), rex.makeCall(cross, l, r), Set.of(),
                JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, after);

        var twoConjuncts = rex.makeCall(SqlStdOperatorTable.AND, rex.makeCall(leftOnly, l), rex.makeCall(cross, l, r));
        RuleChecks.doesNotApply(rule,
                LogicalJoin.create(left, right, List.of(), twoConjuncts, Set.of(), JoinRelType.INNER));
        RuleChecks.doesNotApply(rule,
                LogicalJoin.create(left, right, List.of(), condition, Set.of(), JoinRelType.LEFT));
    }

    public static void main(String[] args) {
        System.out.println("Running JoinConditionPush test...");
        runTest();
    }
}
