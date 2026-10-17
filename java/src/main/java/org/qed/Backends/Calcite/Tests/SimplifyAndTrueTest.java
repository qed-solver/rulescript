package org.qed.Backends.Calcite.Tests;

import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.SimplifyAndTrue;

import java.util.List;

public class SimplifyAndTrueTest {
    public static void runTest() {
        var rule = new SimplifyAndTrue();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        var qedBuilder = org.qed.RuleBuilder.create();
        var table = RuleChecks.table(qedBuilder, 1, false);
        var builder = RuleChecks.ruleBuilder(qedBuilder);
        RelNode input = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        RexNode predicate = RuleChecks.gt(builder, rex.makeInputRef(input, 0), 4);
        RexNode condition = rex.makeCall(SqlStdOperatorTable.AND, predicate, rex.makeLiteral(true));
        RelNode before = LogicalFilter.create(input, condition);
        RelNode expected = LogicalFilter.create(input, predicate);
        RuleChecks.rewrites(rule, before, expected);

        RexNode nonTrueCondition = rex.makeCall(SqlStdOperatorTable.AND, predicate, rex.makeLiteral(false));
        RuleChecks.doesNotApply(rule, LogicalFilter.create(input, nonTrueCondition));
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyAndTrue test...");
        runTest();
        if (CalciteTester.failures() != 0) {
            throw new AssertionError("SimplifyAndTrue test had " + CalciteTester.failures() + " failure(s)");
        }
    }
}
