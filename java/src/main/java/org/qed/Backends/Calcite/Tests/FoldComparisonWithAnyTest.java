package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteRuntime;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.RRuleInstances.FoldComparisonWithAny;
import org.qed.RelType;
import org.qed.RuleBuilder;

public class FoldComparisonWithAnyTest {
    public static void runTest() {
        var rule = new FoldComparisonWithAny();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);
        rewritesHandBuiltPositive();
        leavesHandBuiltNoMatch();
    }

    private static void rewritesHandBuiltPositive() {
        var ruleBuilder = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var source = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var falseEq = rex.makeCall(SqlStdOperatorTable.EQUALS, rex.makeLiteral(true), rex.makeLiteral(false));
        var trueEq = rex.makeCall(SqlStdOperatorTable.EQUALS, rex.makeLiteral(true), rex.makeLiteral(true));
        var condition = rex.makeCall(SqlStdOperatorTable.OR, falseEq, trueEq);
        RelNode before = LogicalFilter.create(source, condition);

        var expectedRuleBuilder = RuleBuilder.create();
        var expectedBuilder = RuleChecks.ruleBuilder(expectedRuleBuilder);
        var expectedTable = RuleChecks.table(expectedRuleBuilder, 1, false);
        var expectedSource = expectedBuilder.scan(expectedTable).build();
        RelNode expected = expectedSource;
        RuleChecks.rewrites(new FoldComparisonWithAny(), before, expected);
    }

    private static void leavesHandBuiltNoMatch() {
        var ruleBuilder = RuleBuilder.create();
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var table = RuleChecks.table(ruleBuilder, 1, false);
        var source = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var falseEq = rex.makeCall(SqlStdOperatorTable.EQUALS, rex.makeLiteral(true), rex.makeLiteral(false));
        var falseSecond = rex.makeCall(SqlStdOperatorTable.EQUALS, rex.makeLiteral(true), rex.makeLiteral(false));
        RelNode before = LogicalFilter.create(source, rex.makeCall(SqlStdOperatorTable.OR, falseEq, falseSecond));
        RuleChecks.doesNotApply(new FoldComparisonWithAny(), before);
    }

    public static void main(String[] args) {
        System.out.println("Running FoldComparisonWithAny test...");
        runTest();
    }
}
