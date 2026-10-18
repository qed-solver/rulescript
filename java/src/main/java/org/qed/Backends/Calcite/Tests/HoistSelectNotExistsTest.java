package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.HoistSelectNotExists;
import org.qed.RuleBuilder;

public final class HoistSelectNotExistsTest {
    public static void runTest() {
        var rule = new HoistSelectNotExists();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var left = RuleChecks.table(builder, 1, false);
        var right = RuleChecks.table(builder, 1, false);
        var b = builder.transform(CalciteRuntime::configure);
        RelNode leftScan = b.scan(left).build();
        RelNode rightScan = b.scan(right).build();
        var rightRef = b.getRexBuilder().makeInputRef(rightScan, 0);
        var rightPredicate = b.call(SqlStdOperatorTable.GREATER_THAN, rightRef, b.literal(7));
        RelNode subqueryInput = LogicalFilter.create(rightScan, rightPredicate);
        var notExists = b.not(RexSubQuery.exists(subqueryInput));
        var leftRef = b.getRexBuilder().makeInputRef(leftScan, 0);
        var rest = b.call(SqlStdOperatorTable.GREATER_THAN, leftRef, b.literal(3));
        RelNode before = LogicalFilter.create(leftScan, b.and(notExists, rest));

        RelNode after = b.push(leftScan).push(subqueryInput).join(JoinRelType.ANTI, b.literal(true))
                .filter(rest).build();
        RuleChecks.rewrites(rule, before, after);

        var unnegatedExists = RexSubQuery.exists(subqueryInput);
        RelNode noMatch = LogicalFilter.create(leftScan, b.and(unnegatedExists, rest));
        RuleChecks.doesNotApply(rule, noMatch);
    }

    public static void main(String[] args) {
        System.out.println("Running HoistSelectNotExists test...");
        runTest();
    }
}
