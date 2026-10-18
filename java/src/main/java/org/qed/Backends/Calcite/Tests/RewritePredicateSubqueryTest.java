package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.RewritePredicateSubquery;
import org.qed.RuleBuilder;
import org.qed.RelType;
import kala.tuple.Tuple;

public class RewritePredicateSubqueryTest {
    public static void runTest() {
        var rule = new RewritePredicateSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        concreteExistsRewritesToSemiJoin();
        rejectsDifferentCorrelationPredicate();
    }

    private static void concreteExistsRewritesToSemiJoin() {
        var qed = RuleBuilder.create();
        var leftName = RuleChecks.table(qed, 1, false, false);
        var rightName = RuleChecks.table(qed, 1, false, false);
        var b = qed.transform(CalciteRuntime::configure);
        var left = b.scan(leftName).build();
        var right = b.scan(rightName).build();
        var rb = b.getRexBuilder();
        var id = b.getCluster().createCorrel();
        var corr = rb.makeCorrel(left.getRowType(), id);
        RexNode equal = rb.makeCall(SqlStdOperatorTable.EQUALS, rb.makeFieldAccess(corr, 0),
                rb.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 0));
        RelNode existsInput = LogicalFilter.create(right, equal);
        RelNode before = LogicalFilter.create(left, RexSubQuery.exists(existsInput),
                com.google.common.collect.ImmutableSet.of(id));
        RelNode expected = b.push(left).push(right).join(JoinRelType.SEMI,
                b.call(SqlStdOperatorTable.EQUALS, b.field(2, 1, 0), b.field(2, 0, 0))).build();
        RuleChecks.rewrites(new RewritePredicateSubquery(), before, expected);
        System.out.println("succeeded (independently built concrete EXISTS case)");
    }

    private static void rejectsDifferentCorrelationPredicate() {
        var qed = RuleBuilder.create();
        var l = RuleChecks.table(qed, 1, false, false);
        var r = RuleChecks.table(qed, 1, false, false);
        var b = qed.transform(CalciteRuntime::configure);
        var left = b.scan(l).build();
        var right = b.scan(r).build();
        var rb = b.getRexBuilder();
        var id = b.getCluster().createCorrel();
        var corr = rb.makeCorrel(left.getRowType(), id);
        RexNode gt = rb.makeCall(SqlStdOperatorTable.GREATER_THAN, rb.makeFieldAccess(corr, 0),
                rb.makeInputRef(right.getRowType().getFieldList().get(0).getType(), 0));
        RelNode sub = LogicalFilter.create(right, gt);
        RelNode plan = LogicalFilter.create(left, RexSubQuery.exists(sub),
                com.google.common.collect.ImmutableSet.of(id));
        RuleChecks.doesNotApply(new RewritePredicateSubquery(), plan);
        System.out.println("succeeded (non-equality correlation rejected)");
    }

    public static void main(String[] args) {
        System.out.println("Running RewritePredicateSubquery test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError(CalciteTester.failures() + " RewritePredicateSubquery checks failed");
        }
    }
}
