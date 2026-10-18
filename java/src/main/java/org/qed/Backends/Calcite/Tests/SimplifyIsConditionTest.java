package org.qed.Backends.Calcite.Tests;

import kala.collection.Seq;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalFilter;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.qed.Backends.Calcite.CalciteTester;
import org.qed.QedTable;
import org.qed.RRuleInstances.SimplifyIsCondition;
import org.qed.RexRN;
import org.qed.RuleBuilder;

public class SimplifyIsConditionTest {
    public static void runTest() {
        var rule = new SimplifyIsCondition();
        RuleChecks.rewritesPattern(rule);
        RuleChecks.rewritesConcretePredicates(rule);
        RuleChecks.rejectsNearMisses(rule);

        RuleChecks.rewrites(rule, manualPlan(true), manualPlan(false));
        RuleChecks.doesNotApply(rule, manualPlanWithNullableLeft());
    }

    private static RelNode manualPlan(boolean isNotDistinct) {
        var ruleBuilder = RuleBuilder.create();
        var table = table(ruleBuilder, false);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var input = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var left = rex.makeInputRef(input.getRowType().getFieldList().get(0).getType(), 0);
        var right = rex.makeInputRef(input.getRowType().getFieldList().get(1).getType(), 1);
        RexNode condition = rex.makeCall(isNotDistinct ? SqlStdOperatorTable.IS_NOT_DISTINCT_FROM
                : SqlStdOperatorTable.EQUALS, left, right);
        var withOther = LogicalFilter.create(input, rex.makeCall(SqlStdOperatorTable.IS_NULL, right));
        return LogicalFilter.create(withOther, condition);
    }

    private static RelNode manualPlanWithNullableLeft() {
        var ruleBuilder = RuleBuilder.create();
        var table = table(ruleBuilder, true);
        var builder = RuleChecks.ruleBuilder(ruleBuilder);
        var input = builder.scan(table).build();
        var rex = builder.getRexBuilder();
        var left = rex.makeInputRef(input.getRowType().getFieldList().get(0).getType(), 0);
        var right = rex.makeInputRef(input.getRowType().getFieldList().get(1).getType(), 1);
        var withOther = LogicalFilter.create(input, rex.makeCall(SqlStdOperatorTable.IS_NULL, right));
        return LogicalFilter.create(withOther,
                rex.makeCall(SqlStdOperatorTable.IS_NOT_DISTINCT_FROM, left, right));
    }

    private static String table(RuleBuilder builder, boolean nullableLeft) {
        var table = new QedTable("T", Seq.of("left_col", "right_col"),
                Seq.of(RexRN.varType("V", nullableLeft), RexRN.varType("V", true)),
                kala.collection.Set.empty(), kala.collection.Set.empty());
        builder.addTable(table);
        return table.getName();
    }

    public static void main(String[] args) {
        System.out.println("Running SimplifyIsCondition test...");
        runTest();
    }
}
