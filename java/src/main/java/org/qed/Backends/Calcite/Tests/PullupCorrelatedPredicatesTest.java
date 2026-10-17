package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RuleBuilder;
import org.qed.RRuleInstances.PullupCorrelatedPredicates;

public class PullupCorrelatedPredicatesTest {
    public static void runTest() {
        var rule = new PullupCorrelatedPredicates();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        ordinaryTables(rule);
    }

    private static void ordinaryTables(PullupCorrelatedPredicates rule) {
        var builder = RuleBuilder.create();
        var outerTable = RuleChecks.table(builder, 1, false);
        var innerTable = RuleChecks.table(builder, 1, false);
        var plain = RuleChecks.ruleBuilder(builder);
        var outer = plain.scan(outerTable).build();
        var inner = plain.scan(innerTable).build();
        var rex = plain.getRexBuilder();
        var local = RuleChecks.gt(plain, rex.makeInputRef(inner.getRowType(), 0), 0);
        var outerCond = RuleChecks.gt(plain, rex.makeInputRef(outer.getRowType(), 0), 0);
        var pulled = rex.makeCall(builder.genericPredicateOp("pulled", false));
        var exists = RexSubQuery.exists(LogicalFilter.create(inner,
                rex.makeCall(SqlStdOperatorTable.AND, pulled, local)));
        var before = LogicalFilter.create(outer, rex.makeCall(SqlStdOperatorTable.AND, outerCond, exists));
        var expectedExists = RexSubQuery.exists(LogicalFilter.create(inner, local));
        var expected = LogicalFilter.create(outer,
                rex.makeCall(SqlStdOperatorTable.AND, outerCond, pulled, expectedExists));
        RuleChecks.rewrites(rule, before, expected);
        var other = LogicalFilter.create(outer, rex.makeCall(SqlStdOperatorTable.AND, outerCond,
                RexSubQuery.exists(LogicalFilter.create(inner, local))));
        RuleChecks.doesNotApply(rule, other);
    }

    public static void main(String[] args) {
        System.out.println("Running PullupCorrelatedPredicates test...");
        runTest();
    }
}
