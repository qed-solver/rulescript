package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.EliminateNot;
import org.qed.RuleBuilder;

public final class EliminateNotTest {
    public static void runTest() {
        var rule = new EliminateNot();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var builder = RuleBuilder.create();
        var table = RuleChecks.table(builder, 1, false, false);
        RelNode scan = RuleChecks.ruleBuilder(builder).scan(table).build();
        var rexBuilder = builder.transform(org.qed.Backends.Calcite.CalciteRuntime::configure)
                .getRexBuilder();
        RexNode comparison = RuleChecks.gt(RuleChecks.ruleBuilder(builder),
                rexBuilder.makeInputRef(scan, 0), 7);
        RexNode doubleNot = rexBuilder.makeCall(SqlStdOperatorTable.NOT,
                rexBuilder.makeCall(SqlStdOperatorTable.NOT, comparison));
        RelNode before = LogicalFilter.create(scan, doubleNot);
        RelNode expected = LogicalFilter.create(scan, comparison);
        RuleChecks.rewrites(rule, before, expected);

        RexNode singleNot = rexBuilder.makeCall(SqlStdOperatorTable.NOT, comparison);
        RuleChecks.doesNotApply(rule, LogicalFilter.create(scan, singleNot));
    }

    public static void main(String[] args) {
        System.out.println("Running EliminateNot test...");
        runTest();
        if (CalciteTester.failures() > 0) {
            System.exit(1);
        }
    }
}
