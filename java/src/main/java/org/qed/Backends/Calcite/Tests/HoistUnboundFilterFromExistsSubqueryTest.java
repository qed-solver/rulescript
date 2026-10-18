package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexSubQuery;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.RRuleInstances.HoistUnboundFilterFromExistsSubquery;
import org.qed.RuleBuilder;

public final class HoistUnboundFilterFromExistsSubqueryTest {
    public static void runTest() {
        var rule = new HoistUnboundFilterFromExistsSubquery();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var outerName = RuleChecks.table(builder, 1, false);
        var innerName = RuleChecks.table(builder, 1, false);
        var b = builder.transform(CalciteRuntime::configure);
        RelNode outer = b.scan(outerName).build();
        RelNode inner = b.scan(innerName).build();
        var outerRef = b.getRexBuilder().makeInputRef(outer, 0);
        var innerRef = b.getRexBuilder().makeInputRef(inner, 0);
        var id = b.getCluster().createCorrel();
        var corr = b.getRexBuilder().makeCorrel(outer.getRowType(), id);
        var correlatedValue = b.getRexBuilder().makeFieldAccess(corr, 0);
        RexNode outerBound = b.call(SqlStdOperatorTable.GREATER_THAN, correlatedValue, b.literal(3));
        RexNode innerBound = b.call(SqlStdOperatorTable.GREATER_THAN, innerRef, b.literal(7));
        RelNode filteredInner = LogicalFilter.create(inner, b.and(outerBound, innerBound));
        RexNode exists = RexSubQuery.exists(filteredInner);
        RelNode before = LogicalFilter.create(outer, exists);
        RelNode after = LogicalFilter.create(outer,
                b.and(outerBound, RexSubQuery.exists(LogicalFilter.create(inner, innerBound))));
        RuleChecks.rewrites(rule, before, after);

        var innerOnlyExists = RexSubQuery.exists(LogicalFilter.create(inner, innerBound));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(outer,
                b.and(innerOnlyExists, b.call(SqlStdOperatorTable.GREATER_THAN, outerRef, b.literal(3)))));
    }

    public static void main(String[] args) {
        System.out.println("Running HoistUnboundFilterFromExistsSubquery test...");
        runTest();
    }
}
