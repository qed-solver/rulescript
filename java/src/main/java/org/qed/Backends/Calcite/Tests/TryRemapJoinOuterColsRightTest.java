package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.SqlOperator;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.TryRemapJoinOuterColsRight;
import org.qed.RuleBuilder;

public final class TryRemapJoinOuterColsRightTest {
    public static void runTest() {
        var rule = new TryRemapJoinOuterColsRight();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        // Hand-assemble the scoped plan from table scans and Calcite correlate nodes.
        var qed = RuleBuilder.create();
        var leftTable = RuleChecks.table(qed, 1, false, true);
        var rightTable = RuleChecks.table(qed, 1, false, true);
        var b = RuleChecks.ruleBuilder(qed);
        RelNode left = b.scan(leftTable).build();
        RelNode right = b.scan(rightTable).build();
        SqlOperator h = RuleBuilder.create().genericPredicateOp("h", true);
        RelNode correlated = RuleChecks.correlate(b, left, right, JoinRelType.INNER,
                args -> b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS, args[0], args[1]));
        var source = LogicalFilter.create(correlated, predicate(b, h, correlated, 1));
        var expected = LogicalFilter.create(correlated, predicate(b, h, correlated, 0));
        RuleChecks.rewrites(rule, source, expected);

        // The same outer predicate over a LEFT correlate is outside this rule's scope.
        RelNode leftCorrelated = RuleChecks.correlate(b, left, right, JoinRelType.LEFT,
                args -> b.call(org.apache.calcite.sql.fun.SqlStdOperatorTable.EQUALS, args[0], args[1]));
        var nearMiss = LogicalFilter.create(leftCorrelated, predicate(b, h, leftCorrelated, 1));
        RuleChecks.doesNotApply(rule, nearMiss);
    }

    private static org.apache.calcite.rex.RexNode predicate(org.apache.calcite.tools.RelBuilder b, SqlOperator h, RelNode input, int index) {
        var type = input.getRowType().getFieldList().get(index).getType();
        var ref = b.getRexBuilder().makeInputRef(type, index);
        return b.getRexBuilder().makeCall(h, ref);
    }

    public static void main(String[] args) {
        System.out.println("Running TryRemapJoinOuterColsRight test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
