package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.PushExtraPredicateThroughJoin;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

public class PushExtraPredicateThroughJoinTest {
    public static void runTest() {
        var rule = new PushExtraPredicateThroughJoin();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PushExtraPredicateThroughJoin rule) {
        var qed = RuleBuilder.create();
        var leftTable = RuleChecks.table(qed, 1, false);
        var rightTable = RuleChecks.table(qed, 1, false);
        var p = qed.genericPredicateOp("p", true);
        var q = qed.genericPredicateOp("q", true);
        var s = qed.genericPredicateOp("s", true);
        var other = qed.genericPredicateOp("other", true);
        var b = RuleChecks.ruleBuilder(qed);
        var rex = b.getRexBuilder();
        var left = b.scan(leftTable).build();
        var right = b.scan(rightTable).build();
        var rightType = right.getRowType().getFieldList().get(0).getType();
        var l = rex.makeInputRef(left, 0);
        var r = rex.makeInputRef(rightType, 1);

        var condition = rex.makeCall(SqlStdOperatorTable.OR,
                rex.makeCall(SqlStdOperatorTable.AND, rex.makeCall(p, l), rex.makeCall(q, r)),
                rex.makeCall(SqlStdOperatorTable.AND, rex.makeCall(p, l), rex.makeCall(s, r)));
        var before = LogicalJoin.create(left, right, List.of(), condition, Set.of(), JoinRelType.INNER);
        var pushedLeft = LogicalFilter.create(left, rex.makeCall(p, l));
        var pushedRight = LogicalFilter.create(right, rex.makeCall(SqlStdOperatorTable.OR,
                rex.makeCall(q, rex.makeInputRef(right, 0)), rex.makeCall(s, rex.makeInputRef(right, 0))));
        var after = LogicalJoin.create(pushedLeft, pushedRight, List.of(), condition, Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, after);

        var differentLeft = rex.makeCall(SqlStdOperatorTable.OR,
                rex.makeCall(SqlStdOperatorTable.AND, rex.makeCall(p, l), rex.makeCall(q, r)),
                rex.makeCall(SqlStdOperatorTable.AND, rex.makeCall(other, l), rex.makeCall(s, r)));
        RuleChecks.doesNotApply(rule,
                LogicalJoin.create(left, right, List.of(), differentLeft, Set.of(), JoinRelType.INNER));
    }

    public static void main(String[] args) {
        System.out.println("Running PushExtraPredicateThroughJoin test...");
        runTest();
    }
}
