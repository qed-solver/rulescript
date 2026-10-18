package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.RRuleInstances.SimplifyTrueAnd;
import org.qed.RuleBuilder;

public class SimplifyTrueAndTest {
    public static void runTest() {
        var rule = new SimplifyTrueAnd();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        handBuiltMatch();
        meaningfulNoMatch();
    }

    private static void handBuiltMatch() {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, false);
        var builder = RuleChecks.ruleBuilder(qed);
        var scan = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var column = rex.makeInputRef(scan, 0);
        var predicate = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, column, rex.makeExactLiteral(
                java.math.BigDecimal.ONE));
        var condition = rex.makeCall(SqlStdOperatorTable.AND, rex.makeLiteral(true), predicate);
        RelNode before = LogicalFilter.create(scan, condition);
        RelNode expected = LogicalFilter.create(scan, predicate);
        RuleChecks.rewrites(new SimplifyTrueAnd(), before, expected);
    }

    private static void meaningfulNoMatch() {
        var qed = RuleBuilder.create();
        var table = RuleChecks.table(qed, 1, false, false);
        var builder = RuleChecks.ruleBuilder(qed);
        var scan = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var column = rex.makeInputRef(scan, 0);
        var predicate = rex.makeCall(SqlStdOperatorTable.GREATER_THAN, column, rex.makeExactLiteral(
                java.math.BigDecimal.ONE));
        RuleChecks.doesNotApply(new SimplifyTrueAnd(), LogicalFilter.create(scan, predicate));
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyTrueAnd test...");
        runTest();
    }
}
