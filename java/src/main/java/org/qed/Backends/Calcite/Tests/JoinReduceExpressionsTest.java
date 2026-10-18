package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalJoin;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.JoinReduceExpressions;
import org.qed.RuleBuilder;

import java.util.List;
import java.util.Set;

public class JoinReduceExpressionsTest {
    public static void runTest() {
        var rule = new JoinReduceExpressions();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(JoinReduceExpressions rule) {
        var qed = RuleBuilder.create();
        var leftTable = RuleChecks.table(qed, 1, false);
        var rightTable = RuleChecks.table(qed, 1, false);
        var b = RuleChecks.ruleBuilder(qed);
        var rex = b.getRexBuilder();
        var left = b.scan(leftTable).build();
        var right = b.scan(rightTable).build();
        var predicate = RuleChecks.eq(b, rex.makeInputRef(left, 0),
                rex.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 1));

        var withFalse = rex.makeCall(SqlStdOperatorTable.AND, predicate, rex.makeLiteral(false));
        var before = LogicalJoin.create(left, right, List.of(), withFalse, Set.of(), JoinRelType.INNER);
        var after = LogicalJoin.create(left, right, List.of(), rex.makeLiteral(false), Set.of(), JoinRelType.INNER);
        RuleChecks.rewrites(rule, before, after);

        var withTrue = rex.makeCall(SqlStdOperatorTable.AND, predicate, rex.makeLiteral(true));
        RuleChecks.doesNotApply(rule,
                LogicalJoin.create(left, right, List.of(), withTrue, Set.of(), JoinRelType.INNER));
    }

    public static void main(String[] args) {
        System.out.println("Running JoinReduceExpressions test...");
        runTest();
    }
}
